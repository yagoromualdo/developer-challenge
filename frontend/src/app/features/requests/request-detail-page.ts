import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  WritableSignal,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, Subject, catchError, map, of, switchMap } from 'rxjs';
import { ROLE_LABELS } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/http/api-problem';
import {
  CommentDialog,
  CommentDialogData,
  CommentDialogResult,
} from '../../shared/components/comment-dialog';
import { MoneyPipe } from '../../shared/components/money.pipe';
import { ProblemAlert } from '../../shared/components/problem-alert';
import { StatusBadge } from '../../shared/components/status-badge';
import { CATEGORY_LABELS } from '../../shared/request-category';
import { REQUEST_STATUS_LABELS } from '../../shared/request-status';
import { HISTORY_ACTION_LABELS, STEP_LEVEL_LABELS, STEP_STATUS_LABELS } from './request-labels';
import { CONFLICT_MESSAGE } from './request-messages';
import {
  ApprovalStep,
  CostCenter,
  PurchaseRequestDetail,
  RequestAction,
  RequestEvent,
  RequestItem,
  RequestVersion,
  RequestVersionSummary,
  TransitionAction,
} from './requests.models';
import { RequestsService } from './requests.service';
import { parseRouteId } from './route-id';

interface Loadable<T> {
  loading: boolean;
  data: T | null;
  error: unknown;
}

/** Conteúdo exibido: o da versão atual (detalhe) ou o de uma versão anterior escolhida na lista. */
interface ShownContent {
  version: number;
  current: boolean;
  title: string;
  description: string;
  category: string;
  neededBy: string | null;
  total: string;
  submittedAt: string | null;
  competenceMonth: string | null;
  items: RequestItem[];
  steps: ApprovalStep[];
}

interface ActionConfig {
  label: string;
  success: string;
  appearance: 'flat' | 'stroked';
  /** Ações com modal: rejeitar e devolver exigem comentário; aprovar e cancelar pedem confirmação. */
  dialog?: CommentDialogData;
}

interface ActionButton {
  action: RequestAction;
  label: string;
  appearance: 'flat' | 'stroked';
}

/** Só define a ordem e o texto dos botões; quais aparecem vem exclusivamente de `allowedActions`. */
const ACTION_ORDER: readonly RequestAction[] = [
  'EDIT',
  'SUBMIT',
  'APPROVE',
  'REQUEST_CHANGES',
  'REJECT',
  'CANCEL',
];

const ACTIONS: Record<RequestAction, ActionConfig> = {
  EDIT: { label: 'Editar', success: '', appearance: 'stroked' },
  SUBMIT: {
    label: 'Enviar para aprovação',
    success: 'Solicitação enviada para aprovação.',
    appearance: 'flat',
  },
  APPROVE: {
    label: 'Aprovar',
    success: 'Etapa aprovada.',
    appearance: 'flat',
    dialog: { title: 'Aprovar etapa', confirmLabel: 'Aprovar', required: false },
  },
  REQUEST_CHANGES: {
    label: 'Solicitar ajustes',
    success: 'Ajustes solicitados ao solicitante.',
    appearance: 'stroked',
    dialog: { title: 'Solicitar ajustes', confirmLabel: 'Solicitar ajustes', required: true },
  },
  REJECT: {
    label: 'Rejeitar',
    success: 'Solicitação rejeitada.',
    appearance: 'stroked',
    dialog: { title: 'Rejeitar solicitação', confirmLabel: 'Rejeitar', required: true },
  },
  CANCEL: {
    label: 'Cancelar solicitação',
    success: 'Solicitação cancelada.',
    appearance: 'stroked',
    dialog: {
      title: 'Cancelar solicitação',
      confirmLabel: 'Cancelar solicitação',
      required: false,
    },
  },
};

const IDLE: Loadable<never> = { loading: false, data: null, error: null };

/** Detalhe da solicitação: versão atual, versões anteriores, cadeia, histórico e ações da API. */
@Component({
  selector: 'app-request-detail-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatProgressBarModule,
    MoneyPipe,
    ProblemAlert,
    StatusBadge,
  ],
  templateUrl: './request-detail-page.html',
  styleUrl: './request-detail-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RequestDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly requests = inject(RequestsService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly currentUser = inject(AuthService).currentUser;

  private readonly requestId = parseRouteId(this.route.snapshot.paramMap.get('id'));
  private readonly reloads = new Subject<void>();
  private readonly versionPicks = new Subject<number>();

  protected readonly detail = signal<Loadable<PurchaseRequestDetail>>({ ...IDLE, loading: true });
  protected readonly versions = signal<Loadable<RequestVersionSummary[]>>({
    ...IDLE,
    loading: true,
  });
  protected readonly history = signal<Loadable<RequestEvent[]>>({ ...IDLE, loading: true });
  /** `null` mostra a versão atual; um número mostra a versão anterior carregada em `pickedVersion`. */
  protected readonly selectedVersion = signal<number | null>(null);
  protected readonly pickedVersion = signal<Loadable<RequestVersion>>(IDLE);
  protected readonly costCenters = signal<CostCenter[]>([]);
  protected readonly acting = signal<RequestAction | null>(null);
  protected readonly actionError = signal<unknown>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly statusLabels = REQUEST_STATUS_LABELS;
  protected readonly stepLevelLabels = STEP_LEVEL_LABELS;
  protected readonly stepStatusLabels = STEP_STATUS_LABELS;
  protected readonly historyLabels = HISTORY_ACTION_LABELS;
  protected readonly roleLabels = ROLE_LABELS;

  protected readonly actions = computed<ActionButton[]>(() => {
    const allowed = this.detail().data?.allowedActions ?? [];
    return ACTION_ORDER.filter((action) => allowed.includes(action)).map((action) => ({
      action,
      label: ACTIONS[action].label,
      appearance: ACTIONS[action].appearance,
    }));
  });

  protected readonly shown = computed<ShownContent | null>(() => {
    const selected = this.selectedVersion();
    if (selected !== null) {
      const version = this.pickedVersion().data;
      return version && version.version === selected ? toShown(version, version.current) : null;
    }
    const detail = this.detail().data;
    return detail ? toShown(detail, true) : null;
  });

  protected readonly costCenterLabel = computed(() => {
    const id = this.detail().data?.costCenterId;
    const costCenter = this.costCenters().find((candidate) => candidate.id === id);
    return costCenter ? `${costCenter.code} · ${costCenter.name}` : id ? `#${id}` : '';
  });

  constructor() {
    if (this.requestId === null) {
      const notFound = new ApiError(404, null, null);
      this.detail.set({ loading: false, data: null, error: notFound });
      this.versions.set(IDLE);
      this.history.set(IDLE);
      return;
    }
    const id = this.requestId;
    this.connect(this.detail, () => this.requests.get(id));
    this.connect(this.versions, () => this.requests.versions(id));
    this.connect(this.history, () => this.requests.history(id));
    this.versionPicks
      .pipe(
        switchMap((version) => track(this.requests.version(id, version))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.pickedVersion.set(state));

    this.requests
      .costCenters()
      .pipe(takeUntilDestroyed(this.destroyRef))
      // Sem a lista, o centro de custo aparece só pelo id; não vale um erro na tela por isso.
      .subscribe({
        next: (costCenters) => this.costCenters.set(costCenters),
        error: () => this.costCenters.set([]),
      });

    this.reload();
  }

  protected reload(): void {
    this.selectedVersion.set(null);
    this.reloads.next();
  }

  protected showCurrent(): void {
    this.selectedVersion.set(null);
  }

  protected selectVersion(version: RequestVersionSummary): void {
    if (version.current) {
      this.showCurrent();
      return;
    }
    this.selectedVersion.set(version.version);
    this.pickedVersion.set({ ...IDLE, loading: true });
    this.versionPicks.next(version.version);
  }

  protected retryVersion(): void {
    const selected = this.selectedVersion();
    if (selected !== null) {
      this.pickedVersion.set({ ...IDLE, loading: true });
      this.versionPicks.next(selected);
    }
  }

  protected run(action: RequestAction): void {
    const current = this.detail().data;
    if (!current || this.acting() || action === 'EDIT') {
      return;
    }
    const dialogData = ACTIONS[action].dialog;
    if (!dialogData) {
      this.execute(current, action);
      return;
    }
    this.dialog
      .open<CommentDialog, CommentDialogData, CommentDialogResult>(CommentDialog, {
        data: dialogData,
        width: '520px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.execute(current, action, result.comment);
        }
      });
  }

  protected userLabel(userId: number | null): string {
    if (userId === null) {
      return '';
    }
    return userId === this.currentUser()?.id ? 'você' : `usuário #${userId}`;
  }

  /** `"2026-11"` → `"11/2026"`; a competência só existe depois do envio. */
  protected monthLabel(month: string | null): string {
    const [year, monthNumber] = month?.split('-') ?? [];
    return year && monthNumber ? `${monthNumber}/${year}` : '—';
  }

  protected isBusy(): boolean {
    return this.acting() !== null || this.detail().loading;
  }

  /** O `lockVersion` é o do detalhe em que o usuário decidiu: se mudou desde então, o back responde 409. */
  private execute(
    current: PurchaseRequestDetail,
    action: TransitionAction,
    comment?: string,
  ): void {
    this.acting.set(action);
    this.actionError.set(null);
    this.notice.set(null);
    this.requests.transition(current.id, action, current.lockVersion, comment).subscribe({
      next: () => {
        this.acting.set(null);
        this.snackBar.open(ACTIONS[action].success, 'Fechar', { duration: 5000 });
        this.reload();
      },
      error: (error: unknown) => {
        this.acting.set(null);
        if (error instanceof ApiError && error.status === 409) {
          this.notice.set(CONFLICT_MESSAGE);
        } else {
          this.actionError.set(error);
        }
        // Mesmo no erro o estado pode ter mudado (etapa decidida por outro, orçamento consumido).
        this.reload();
      },
    });
  }

  private connect<T>(target: WritableSignal<Loadable<T>>, fetch: () => Observable<T>): void {
    this.reloads
      .pipe(
        switchMap(() => {
          target.update((state) => ({ ...state, loading: true, error: null }));
          return track(fetch());
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => target.set(state));
  }
}

function track<T>(source: Observable<T>): Observable<Loadable<T>> {
  return source.pipe(
    map((data): Loadable<T> => ({ loading: false, data, error: null })),
    catchError((error: unknown) => of<Loadable<T>>({ loading: false, data: null, error })),
  );
}

function toShown(content: PurchaseRequestDetail | RequestVersion, current: boolean): ShownContent {
  return {
    version: content.version,
    current,
    title: content.title,
    description: content.description,
    category: CATEGORY_LABELS[content.category],
    neededBy: content.neededBy,
    total: content.total,
    submittedAt: content.submittedAt,
    competenceMonth: content.competenceMonth,
    items: content.items,
    steps: content.steps,
  };
}
