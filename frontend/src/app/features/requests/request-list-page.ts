import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  FormGroupDirective,
  NgForm,
  ReactiveFormsModule,
  ValidationErrors,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ErrorStateMatcher } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, distinctUntilChanged, map, merge, of, switchMap, tap } from 'rxjs';
import { ApiError } from '../../core/http/api-problem';
import { Page } from '../../core/http/page';
import { MoneyPipe } from '../../shared/components/money.pipe';
import { PtBrPaginatorIntl } from '../../shared/components/paginator-intl';
import { ProblemAlert } from '../../shared/components/problem-alert';
import { StatusBadge } from '../../shared/components/status-badge';
import { CATEGORIES, CATEGORY_LABELS, Category } from '../../shared/request-category';
import {
  REQUEST_STATUSES,
  REQUEST_STATUS_LABELS,
  RequestStatus,
} from '../../shared/request-status';
import {
  DEFAULT_LIST_QUERY,
  PAGE_SIZE_OPTIONS,
  RequestListQuery,
  SORT_FIELDS,
  SortField,
  hasFilters,
  isCanonical,
  parseListQuery,
  sameQuery,
  toQueryParams,
} from './request-list-query';
import { CostCenter, PurchaseRequestSummary } from './requests.models';
import { RequestsService } from './requests.service';

interface ListState {
  loading: boolean;
  result: Page<PurchaseRequestSummary> | null;
  error: unknown;
}

function createdRangeValidator(group: AbstractControl): ValidationErrors | null {
  const { createdFrom, createdTo } = group.value as { createdFrom: string; createdTo: string };
  return createdFrom && createdTo && createdTo < createdFrom ? { createdRange: true } : null;
}

/** Marca "Criada até" quando o intervalo do grupo está invertido. */
class CreatedRangeErrorMatcher implements ErrorStateMatcher {
  isErrorState(control: AbstractControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const invalid = !!control && (control.invalid || !!control.parent?.hasError('createdRange'));
    return invalid && (!!control?.touched || !!form?.submitted);
  }
}

@Component({
  selector: 'app-request-list-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
    MoneyPipe,
    ProblemAlert,
    StatusBadge,
  ],
  providers: [{ provide: MatPaginatorIntl, useClass: PtBrPaginatorIntl }],
  templateUrl: './request-list-page.html',
  styleUrl: './request-list-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RequestListPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly requests = inject(RequestsService);
  private readonly reload$ = new Subject<void>();

  protected readonly statusOptions = REQUEST_STATUSES.map((value) => ({
    value,
    label: REQUEST_STATUS_LABELS[value],
  }));
  protected readonly categoryOptions = CATEGORIES.map((value) => ({
    value,
    label: CATEGORY_LABELS[value],
  }));
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly columns = [
    'title',
    'status',
    'category',
    'costCenter',
    'total',
    'createdAt',
    'updatedAt',
  ];
  protected readonly createdRangeMatcher = new CreatedRangeErrorMatcher();

  protected readonly filters = new FormGroup(
    {
      status: new FormControl<RequestStatus[]>([], { nonNullable: true }),
      category: new FormControl<Category[]>([], { nonNullable: true }),
      costCenterId: new FormControl<number | null>(null),
      createdFrom: new FormControl('', { nonNullable: true }),
      createdTo: new FormControl('', { nonNullable: true }),
    },
    { validators: createdRangeValidator },
  );

  protected readonly query = signal<RequestListQuery>(DEFAULT_LIST_QUERY);
  protected readonly state = signal<ListState>({ loading: true, result: null, error: null });
  protected readonly costCenters = signal<CostCenter[]>([]);
  protected readonly costCentersFailed = signal(false);

  private readonly costCenterById = computed(
    () => new Map(this.costCenters().map((costCenter) => [costCenter.id, costCenter])),
  );

  protected readonly rows = computed(() =>
    (this.state().result?.data ?? []).map((request) => ({
      ...request,
      categoryLabel: CATEGORY_LABELS[request.category] ?? request.category,
      costCenterLabel:
        this.costCenterById().get(request.costCenterId)?.code ?? `#${request.costCenterId}`,
    })),
  );

  protected readonly totalElements = computed(() => this.state().result?.totalElements ?? 0);
  protected readonly filtered = computed(() => hasFilters(this.query()));
  /** A página pedida passou do fim (ex.: link antigo depois de a lista encolher). */
  protected readonly beyondLastPage = computed(() => {
    const result = this.state().result;
    return !!result && result.data.length === 0 && result.totalElements > 0;
  });

  protected readonly fieldErrors = computed(() => {
    const error = this.state().error;
    return error instanceof ApiError && error.status === 400 ? error.fieldErrors : [];
  });

  constructor() {
    const query$ = this.route.queryParamMap.pipe(
      map((params) => {
        const query = parseListQuery(params);
        if (!isCanonical(params, query)) {
          this.navigate(query, true);
        }
        return query;
      }),
      distinctUntilChanged(sameQuery),
      tap((query) => {
        this.query.set(query);
        this.filters.reset(toFilterValue(query));
      }),
    );

    merge(query$, this.reload$.pipe(map(() => this.query())))
      .pipe(
        tap(() => this.state.update((state) => ({ ...state, loading: true, error: null }))),
        switchMap((query) =>
          this.requests.list(query).pipe(
            map((result): ListState => ({ loading: false, result, error: null })),
            catchError((error: unknown) => of<ListState>({ loading: false, result: null, error })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.state.set(state));

    this.requests
      .costCenters()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (costCenters) => this.costCenters.set(costCenters),
        error: () => this.costCentersFailed.set(true),
      });
  }

  protected applyFilters(): void {
    if (this.filters.invalid) {
      this.filters.markAllAsTouched();
      return;
    }
    this.navigate({ ...this.query(), ...fromFilterValue(this.filters.getRawValue()), page: 0 });
  }

  protected clearFilters(): void {
    const { size, sortField, sortDirection } = this.query();
    this.navigate({ ...DEFAULT_LIST_QUERY, size, sortField, sortDirection });
  }

  protected changeSort(sort: Sort): void {
    if (!(SORT_FIELDS as readonly string[]).includes(sort.active)) {
      return;
    }
    this.navigate({
      ...this.query(),
      sortField: sort.active as SortField,
      sortDirection: sort.direction || 'asc',
      page: 0,
    });
  }

  protected changePage(event: PageEvent): void {
    this.navigate({ ...this.query(), page: event.pageIndex, size: event.pageSize });
  }

  protected goToFirstPage(): void {
    this.navigate({ ...this.query(), page: 0 });
  }

  protected reload(): void {
    this.reload$.next();
  }

  private navigate(query: RequestListQuery, replaceUrl = false): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: toQueryParams(query),
      replaceUrl,
    });
  }
}

function toFilterValue(query: RequestListQuery) {
  return {
    status: query.status,
    category: query.category,
    costCenterId: query.costCenterId,
    createdFrom: query.createdFrom ?? '',
    createdTo: query.createdTo ?? '',
  };
}

function fromFilterValue(value: ReturnType<typeof toFilterValue>): Partial<RequestListQuery> {
  return {
    status: REQUEST_STATUSES.filter((status) => value.status.includes(status)),
    category: CATEGORIES.filter((category) => value.category.includes(category)),
    costCenterId: value.costCenterId,
    createdFrom: value.createdFrom || null,
    createdTo: value.createdTo || null,
  };
}
