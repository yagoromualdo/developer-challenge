import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ApiError } from '../../core/http/api-problem';

/** Alerta de erro de uma chamada à API: mostra o `detail` do back e o código para o suporte. */
@Component({
  selector: 'app-problem-alert',
  template: `
    <div class="alert" role="alert">
      <p class="message">{{ message() }}</p>
      @if (requestId(); as id) {
        <p class="reference">Código para o suporte: {{ id }}</p>
      }
      <ng-content />
    </div>
  `,
  styles: `
    .alert {
      padding: 12px 16px;
      border-radius: 8px;
      background-color: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .message {
      margin: 0;
    }
    .reference {
      margin: 4px 0 0;
      font: var(--mat-sys-body-small);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProblemAlert {
  readonly error = input.required<unknown>();
  /** Texto usado quando a API não devolveu um `detail` (rede, 5xx ou erro desconhecido). */
  readonly fallback = input('Não foi possível carregar os dados.');

  protected readonly message = computed(() => {
    const error = this.error();
    if (error instanceof ApiError && error.status === 0) {
      return 'Não foi possível conectar ao servidor.';
    }
    if (error instanceof ApiError && error.status < 500 && error.problem?.detail) {
      return error.problem.detail;
    }
    return this.fallback();
  });

  protected readonly requestId = computed(() => {
    const error = this.error();
    return error instanceof ApiError ? error.requestId : null;
  });
}
