import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError, ApiFieldError } from '../../core/http/api-problem';
import { randomUuid } from '../../core/http/uuid';
import { MoneyPipe } from '../../shared/components/money.pipe';
import { ProblemAlert } from '../../shared/components/problem-alert';
import { CATEGORIES, CATEGORY_LABELS } from '../../shared/request-category';
import {
  ItemForm,
  REQUEST_LIMITS,
  applyServerErrors,
  buildItemForm,
  buildRequestForm,
  controlErrorMessage,
  displayTotal,
  fillRequestForm,
  itemSubtotal,
  toCreatePayload,
  toUpdatePayload,
} from './request-form';
import { CostCenter, PurchaseRequestDetail } from './requests.models';
import { RequestsService } from './requests.service';

interface LoadState {
  loading: boolean;
  detail: PurchaseRequestDetail | null;
  error: unknown;
}

const CONFLICT_MESSAGE =
  'Esta solicitação foi alterada por outra pessoa. Os dados foram atualizados.';
const KEY_REUSED_MESSAGE =
  'Este formulário já criou uma solicitação com outros dados (provavelmente num envio cuja resposta ' +
  'não chegou). Confira suas solicitações antes de tentar de novo; para criar outra, abra um novo formulário.';

/** Criação (`/requests/new`) e edição (`/requests/:id/edit`) da solicitação. */
@Component({
  selector: 'app-request-form-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MoneyPipe,
    ProblemAlert,
  ],
  templateUrl: './request-form-page.html',
  styleUrl: './request-form-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RequestFormPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly requests = inject(RequestsService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly currentUser = inject(AuthService).currentUser;

  /** Uma chave por instância do formulário: duplo clique e novas tentativas reaproveitam a mesma. */
  private readonly idempotencyKey = randomUuid();

  private readonly requestId = parseId(this.route.snapshot.paramMap.get('id'));
  protected readonly editing = this.route.snapshot.paramMap.has('id');
  protected readonly limits = REQUEST_LIMITS;
  protected readonly categoryOptions = CATEGORIES.map((value) => ({
    value,
    label: CATEGORY_LABELS[value],
  }));

  protected readonly form = buildRequestForm();
  protected readonly items = this.form.controls.items;

  protected readonly load = signal<LoadState>({
    loading: this.editing,
    detail: null,
    error: null,
  });
  protected readonly costCenters = signal<CostCenter[]>([]);
  protected readonly costCentersFailed = signal(false);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<unknown>(null);
  protected readonly invalidFields = signal(false);
  protected readonly unmappedErrors = signal<ApiFieldError[]>([]);
  protected readonly notice = signal<string | null>(null);

  private readonly itemValues = toSignal(
    this.items.valueChanges.pipe(map(() => this.items.getRawValue())),
    { initialValue: this.items.getRawValue() },
  );
  protected readonly subtotals = computed(() => this.itemValues().map(itemSubtotal));
  protected readonly total = computed(() => displayTotal(this.itemValues()));
  protected readonly canEdit = computed(
    () => !this.editing || !!this.load().detail?.allowedActions.includes('EDIT'),
  );
  protected readonly backLink = computed(() => {
    const detail = this.load().detail;
    return detail ? ['/requests', detail.id] : ['/requests'];
  });
  protected readonly costCenterLabel = computed(() => {
    const id = this.load().detail?.costCenterId;
    const costCenter = this.costCenters().find((candidate) => candidate.id === id);
    return costCenter ? `${costCenter.code} · ${costCenter.name}` : id ? `#${id}` : '';
  });

  constructor() {
    if (this.editing) {
      this.form.controls.costCenterId.disable();
      this.loadDetail();
    } else {
      this.form.controls.costCenterId.setValue(this.currentUser()?.costCenterId ?? null);
    }

    this.requests
      .costCenters()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (costCenters) => this.costCenters.set(costCenters),
        error: () => this.costCentersFailed.set(true),
      });
  }

  protected addItem(): void {
    if (this.items.length < REQUEST_LIMITS.maxItems) {
      this.items.push(buildItemForm());
    }
  }

  protected removeItem(index: number): void {
    if (this.items.length > 1) {
      this.items.removeAt(index);
    }
  }

  protected errorOf(control: AbstractControl): string | null {
    return controlErrorMessage(control);
  }

  protected itemControls(): ItemForm[] {
    return this.items.controls;
  }

  protected submit(): void {
    if (this.submitting()) {
      return;
    }
    this.submitError.set(null);
    this.unmappedErrors.set([]);
    this.notice.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.invalidFields.set(true);
      return;
    }
    this.invalidFields.set(false);
    this.submitting.set(true);

    this.save().subscribe({
      next: (saved) => {
        this.snackBar.open(
          this.editing ? 'Solicitação atualizada.' : 'Solicitação criada como rascunho.',
          'Fechar',
          { duration: 5000 },
        );
        void this.router.navigate(['/requests', saved.id]);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.handleSaveError(error);
      },
    });
  }

  protected reloadDetail(): void {
    this.loadDetail();
  }

  private save(): Observable<PurchaseRequestDetail> {
    const detail = this.load().detail;
    if (detail) {
      return this.requests.update(detail.id, toUpdatePayload(this.form, detail.lockVersion));
    }
    return this.requests.create(toCreatePayload(this.form), this.idempotencyKey);
  }

  private handleSaveError(error: unknown): void {
    if (!(error instanceof ApiError)) {
      this.submitError.set(error);
      return;
    }
    if (error.status === 400 && error.fieldErrors.length) {
      this.unmappedErrors.set(applyServerErrors(this.form, error.fieldErrors));
      this.invalidFields.set(true);
      return;
    }
    if (error.status === 409 && error.code === 'IDEMPOTENCY_KEY_REUSED') {
      this.notice.set(KEY_REUSED_MESSAGE);
      return;
    }
    if (error.status === 409 && this.editing) {
      this.notice.set(CONFLICT_MESSAGE);
      this.loadDetail();
      return;
    }
    this.submitError.set(error);
  }

  private loadDetail(): void {
    if (this.requestId === null) {
      this.load.set({ loading: false, detail: null, error: new ApiError(404, null, null) });
      return;
    }
    this.load.update((state) => ({ ...state, loading: true, error: null }));
    this.requests.get(this.requestId).subscribe({
      next: (detail) => {
        fillRequestForm(this.form, detail);
        this.load.set({ loading: false, detail, error: null });
      },
      error: (error: unknown) => this.load.set({ loading: false, detail: null, error }),
    });
  }
}

function parseId(raw: string | null): number | null {
  const id = Number(raw);
  return raw !== null && Number.isInteger(id) && id > 0 ? id : null;
}
