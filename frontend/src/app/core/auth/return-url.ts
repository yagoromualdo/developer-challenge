export const DEFAULT_ROUTE = '/dashboard';

/** Aceita só caminhos internos do app; `//host`, URLs absolutas e o próprio login caem na rota padrão. */
export function safeReturnUrl(raw: string | null | undefined): string {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//') || raw.startsWith('/\\')) {
    return DEFAULT_ROUTE;
  }
  return isLoginUrl(raw) ? DEFAULT_ROUTE : raw;
}

export function isLoginUrl(url: string): boolean {
  return url === '/login' || url.startsWith('/login?') || url.startsWith('/login/');
}
