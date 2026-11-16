import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/**
 * Exige sessão e garante os dados do usuário (menu e `roleGuard`). Um 401 em `/auth/me` é tratado pelo
 * `errorInterceptor`, que já leva ao login com o `returnUrl`.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.hasValidSession()) {
    const expired = auth.isLoggedIn();
    auth.clearSession();
    return router.createUrlTree(['/login'], {
      queryParams: expired ? { returnUrl: state.url, expired: true } : { returnUrl: state.url },
    });
  }
  return auth.ensureCurrentUser().pipe(
    map(() => true),
    catchError(() => of(false)),
  );
};
