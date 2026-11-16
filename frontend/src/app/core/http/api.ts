import { HttpContextToken, HttpRequest } from '@angular/common/http';

export const API_BASE_URL = '/api';

export const REQUEST_ID_HEADER = 'X-Request-Id';

/** Marca requisições cujo 401 é tratado pelo chamador (ex.: senha errada no login), sem expirar a sessão. */
export const SKIP_SESSION_EXPIRED = new HttpContextToken<boolean>(() => false);

export function isApiRequest(req: HttpRequest<unknown>): boolean {
  return req.url === API_BASE_URL || req.url.startsWith(`${API_BASE_URL}/`);
}
