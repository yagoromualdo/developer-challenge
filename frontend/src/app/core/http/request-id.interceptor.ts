import { HttpInterceptorFn } from '@angular/common/http';
import { REQUEST_ID_HEADER, isApiRequest } from './api';
import { randomUuid } from './uuid';

/** Um `X-Request-Id` por requisição, para correlacionar a tela com os logs e o histórico do back. */
export const requestIdInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isApiRequest(req) || req.headers.has(REQUEST_ID_HEADER)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { [REQUEST_ID_HEADER]: randomUuid() } }));
};
