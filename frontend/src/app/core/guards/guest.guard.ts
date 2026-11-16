import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { safeReturnUrl } from '../auth/return-url';

/** Quem já tem sessão válida não vê o login: segue direto para o `returnUrl`. */
export const guestGuard: CanActivateFn = (route) => {
  if (!inject(AuthService).hasValidSession()) {
    return true;
  }
  return inject(Router).parseUrl(safeReturnUrl(route.queryParamMap.get('returnUrl')));
};
