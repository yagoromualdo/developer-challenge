import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { Role } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';
import { DEFAULT_ROUTE } from '../auth/return-url';

/** Lê `route.data.roles`. Só UX de navegação: quem autoriza de verdade é o back. */
export const roleGuard: CanActivateFn = (route) => {
  const router = inject(Router);
  const allowed = (route.data['roles'] as readonly Role[] | undefined) ?? [];

  return inject(AuthService)
    .ensureCurrentUser()
    .pipe(
      map((user) =>
        allowed.length === 0 || allowed.includes(user.role)
          ? true
          : router.createUrlTree([DEFAULT_ROUTE]),
      ),
      catchError(() => of(false)),
    );
};
