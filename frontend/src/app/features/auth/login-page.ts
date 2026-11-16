import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { safeReturnUrl } from '../../core/auth/return-url';
import { ApiError } from '../../core/http/api-problem';

type LoginField = 'email' | 'password';

@Component({
  selector: 'app-login-page',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressBarModule,
  ],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Rota de origem, vinda da query string (`withComponentInputBinding`). */
  readonly returnUrl = input<string>();
  /** Presente quando o login foi aberto por sessão expirada. */
  readonly expired = input<string>();

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected submit(): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.auth
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
        error: (error: unknown) => this.showError(error),
      });
  }

  // Rede e 5xx já viram aviso global no errorInterceptor; aqui fica o que é deste formulário.
  private showError(error: unknown): void {
    if (!(error instanceof ApiError) || error.status === 0 || error.status >= 500) {
      return;
    }
    for (const fieldError of error.fieldErrors) {
      const control = this.form.controls[fieldError.field as LoginField];
      control?.setErrors({ server: fieldError.message });
      control?.markAsTouched();
    }
    if (error.fieldErrors.length === 0) {
      this.errorMessage.set(error.problem?.detail ?? 'Não foi possível entrar.');
    }
  }
}
