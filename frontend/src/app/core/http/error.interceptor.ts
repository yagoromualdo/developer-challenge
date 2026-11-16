import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { isLoginUrl } from '../auth/return-url';
import { REQUEST_ID_HEADER, SKIP_SESSION_EXPIRED } from './api';
import { ApiError, toApiError } from './api-problem';

/**
 * Converte toda falha HTTP em `ApiError`. Aqui só o que é transversal (401, rede e 5xx);
 * 400, 409 e 422 dependem do contexto e ficam com os componentes.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((response: unknown) => {
      if (!(response instanceof HttpErrorResponse)) {
        return throwError(() => response);
      }
      const error = toApiError(response, req.headers.get(REQUEST_ID_HEADER));

      if (error.status === 401 && !req.context.get(SKIP_SESSION_EXPIRED)) {
        auth.clearSession();
        redirectToLogin(router);
      } else if (error.status === 0 || error.status >= 500) {
        snackBar.open(genericMessage(error), 'Fechar', { duration: 8000 });
      }
      return throwError(() => error);
    }),
  );
};

function redirectToLogin(router: Router): void {
  const returnUrl = urlBeingShown(router);
  // Vários 401 em paralelo: só o primeiro navega.
  if (isLoginUrl(returnUrl)) {
    return;
  }
  void router.navigate(['/login'], { queryParams: { returnUrl, expired: true } });
}

// Durante uma navegação (ex.: guard chamando /auth/me), `router.url` ainda é a rota anterior.
function urlBeingShown(router: Router): string {
  const navigation = router.currentNavigation();
  const target = navigation?.finalUrl ?? navigation?.extractedUrl;
  return target ? router.serializeUrl(target) : router.url;
}

function genericMessage(error: ApiError): string {
  const reference = error.requestId ? ` Código para o suporte: ${error.requestId}.` : '';
  if (error.status === 0) {
    return `Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.${reference}`;
  }
  return `Ocorreu um erro inesperado no servidor.${reference}`;
}
