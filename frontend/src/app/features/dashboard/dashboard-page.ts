import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { MoneyPipe } from '../../shared/components/money.pipe';
import { ProblemAlert } from '../../shared/components/problem-alert';
import { REQUEST_STATUSES, REQUEST_STATUS_LABELS } from '../../shared/request-status';
import { CostCenterBudget, DashboardSummary } from './dashboard.models';
import { DashboardService } from './dashboard.service';

type DashboardState =
  | { status: 'loading' }
  | { status: 'error'; error: unknown }
  | { status: 'ready'; summary: DashboardSummary };

const MONTH_FORMAT = new Intl.DateTimeFormat('pt-BR', {
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

@Component({
  selector: 'app-dashboard-page',
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatProgressBarModule,
    MoneyPipe,
    ProblemAlert,
  ],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  private readonly dashboard = inject(DashboardService);
  private readonly destroyRef = inject(DestroyRef);
  private loading?: Subscription;

  protected readonly state = signal<DashboardState>({ status: 'loading' });

  private readonly summary = computed(() => {
    const state = this.state();
    return state.status === 'ready' ? state.summary : null;
  });

  protected readonly error = computed(() => {
    const state = this.state();
    return state.status === 'error' ? state.error : null;
  });

  protected readonly monthLabel = computed(() => {
    const month = this.summary()?.month;
    return month ? formatCompetence(month) : '';
  });

  protected readonly totalRequests = computed(() => this.summary()?.totalRequests ?? 0);

  protected readonly statusCards = computed(() => {
    const counts = this.summary()?.requestsByStatus ?? {};
    return REQUEST_STATUSES.map((status) => ({
      status,
      label: REQUEST_STATUS_LABELS[status],
      count: counts[status] ?? 0,
    }));
  });

  protected readonly budgets = computed(() =>
    (this.summary()?.budgets ?? []).map((budget) => ({
      ...budget,
      consumedPercent: consumedPercent(budget),
    })),
  );

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading?.unsubscribe();
    this.state.set({ status: 'loading' });
    this.loading = this.dashboard
      .summary()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (summary) => this.state.set({ status: 'ready', summary }),
        error: (error: unknown) => this.state.set({ status: 'error', error }),
      });
  }
}

function formatCompetence(month: string): string {
  const [year, monthNumber] = month.split('-').map(Number);
  if (!year || !monthNumber) {
    return month;
  }
  return MONTH_FORMAT.format(Date.UTC(year, monthNumber - 1, 1));
}

// Só para a barra de progresso; os valores exibidos vêm prontos do back.
function consumedPercent(budget: CostCenterBudget): number | null {
  const amount = Number(budget.amount);
  const consumed = Number(budget.consumed);
  if (!(amount > 0) || !Number.isFinite(consumed)) {
    return null;
  }
  return Math.min(100, Math.round((consumed / amount) * 100));
}
