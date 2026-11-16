import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { REQUEST_STATUS_LABELS, RequestStatus } from '../request-status';

@Component({
  selector: 'app-status-badge',
  template: `<span class="badge" [attr.data-status]="status()">{{ label() }}</span>`,
  styles: `
    .badge {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 999px;
      font: var(--mat-sys-label-medium);
      white-space: nowrap;
      background-color: var(--mat-sys-surface-container-highest);
      color: var(--mat-sys-on-surface);
    }
    [data-status='PENDING_APPROVAL'] {
      background-color: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    [data-status='CHANGES_REQUESTED'] {
      background-color: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
    }
    [data-status='APPROVED'] {
      background-color: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }
    [data-status='REJECTED'] {
      background-color: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusBadge {
  readonly status = input.required<RequestStatus>();

  protected readonly label = computed(() => REQUEST_STATUS_LABELS[this.status()]);
}
