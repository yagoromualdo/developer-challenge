import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Subject, catchError, map, of, startWith, switchMap, tap } from 'rxjs';
import { ApiError } from '../../core/http/api-problem';
import {
  CommentDialog,
  CommentDialogData,
  CommentDialogResult,
} from '../../shared/components/comment-dialog';
import { MoneyPipe, formatMoney } from '../../shared/components/money.pipe';
import { ProblemAlert } from '../../shared/components/problem-alert';
import { CATEGORY_LABELS } from '../../shared/request-category';
import { STEP_LEVEL_LABELS } from '../requests/request-labels';
import { CONFLICT_MESSAGE } from '../requests/request-messages';
import { CostCenter } from '../requests/requests.models';
import { RequestsService } from '../requests/requests.service';
import { ApprovalInboxItem } from './approvals.models';
import { ApprovalsService } from './approvals.service';

interface InboxState {
  loading: boolean;
  items: ApprovalInboxItem[] | null;
  error: unknown;
}

/**
 * Caixa de aprovação: o que o back diz que o usuário pode aprovar agora. Aprovar sai direto daqui; rejeitar e
 * solicitar ajustes ficam no detalhe, onde os botões vêm do `allowedActions`.
 */
@Component({
  selector: 'app-approval-inbox-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatProgressBarModule,
    MatTableModule,
    MoneyPipe,
    ProblemAlert,
  ],
  templateUrl: './approval-inbox-page.html',
  styleUrl: './approval-inbox-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApprovalInboxPage {
  private readonly approvals = inject(ApprovalsService);
  private readonly requests = inject(RequestsService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly reloads = new Subject<void>();

  protected readonly columns = [
    'title',
    'step',
    'category',
    'costCenter',
    'total',
    'submittedAt',
    'neededBy',
    'actions',
  ];

  protected readonly state = signal<InboxState>({ loading: true, items: null, error: null });
  protected readonly costCenters = signal<CostCenter[]>([]);
  /** Id da solicitação sendo aprovada; uma ação por vez para não disparar decisões em paralelo. */
  protected readonly acting = signal<number | null>(null);
  protected readonly actionError = signal<unknown>(null);
  protected readonly notice = signal<string | null>(null);

  private readonly costCenterById = computed(
    () => new Map(this.costCenters().map((costCenter) => [costCenter.id, costCenter])),
  );

  protected readonly rows = computed(() =>
    (this.state().items ?? []).map((item) => ({
      ...item,
      stepLabel: STEP_LEVEL_LABELS[item.currentStepLevel] ?? item.currentStepLevel,
      categoryLabel: CATEGORY_LABELS[item.category] ?? item.category,
      costCenterLabel:
        this.costCenterById().get(item.costCenterId)?.code ?? `#${item.costCenterId}`,
    })),
  );

  constructor() {
    this.reloads
      .pipe(
        startWith(undefined),
        tap(() => this.state.update((state) => ({ ...state, loading: true, error: null }))),
        switchMap(() =>
          this.approvals.inbox().pipe(
            map((items): InboxState => ({ loading: false, items, error: null })),
            catchError((error: unknown) => of<InboxState>({ loading: false, items: null, error })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.state.set(state));

    this.requests
      .costCenters()
      .pipe(takeUntilDestroyed())
      // Sem a lista, o centro de custo aparece só pelo id; não vale um erro na tela por isso.
      .subscribe({
        next: (costCenters) => this.costCenters.set(costCenters),
        error: () => this.costCenters.set([]),
      });
  }

  protected reload(): void {
    this.reloads.next();
  }

  protected isBusy(): boolean {
    return this.acting() !== null || this.state().loading;
  }

  protected approve(item: ApprovalInboxItem): void {
    if (this.isBusy()) {
      return;
    }
    this.dialog
      .open<CommentDialog, CommentDialogData, CommentDialogResult>(CommentDialog, {
        data: {
          title: 'Aprovar etapa',
          confirmLabel: 'Aprovar',
          required: false,
          message: `${item.title} · ${STEP_LEVEL_LABELS[item.currentStepLevel]} · ${formatMoney(item.total)}`,
        },
        width: '520px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.execute(item, result.comment);
        }
      });
  }

  /** Usa o `lockVersion` do item da caixa: se outra pessoa mexeu na solicitação depois da leitura, vem 409. */
  private execute(item: ApprovalInboxItem, comment: string): void {
    this.acting.set(item.id);
    this.actionError.set(null);
    this.notice.set(null);
    this.requests.transition(item.id, 'APPROVE', item.lockVersion, comment).subscribe({
      next: () => {
        this.acting.set(null);
        this.snackBar.open(`Etapa aprovada: ${item.title}.`, 'Fechar', { duration: 5000 });
        this.reload();
      },
      error: (error: unknown) => {
        this.acting.set(null);
        if (error instanceof ApiError && error.status === 409) {
          this.notice.set(CONFLICT_MESSAGE);
        } else {
          this.actionError.set(error);
        }
        // Mesmo no erro a caixa pode ter mudado (etapa decidida por outro, orçamento consumido).
        this.reload();
      },
    });
  }
}
