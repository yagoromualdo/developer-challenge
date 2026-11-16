import { CurrentUser, LoginResponse, Role } from './auth.models';

export const SESSION_STORAGE_KEY = 'purchase-requests.session';

export function userWith(role: Role): CurrentUser {
  return {
    id: 1,
    name: 'Ana Teste',
    email: 'ana@example.com',
    role,
    approverLevel: role === 'APROVADOR' ? 'NIVEL_1' : null,
    costCenterId: 1,
  };
}

export function loginResponse(user: CurrentUser = userWith('SOLICITANTE')): LoginResponse {
  return {
    accessToken: 'token-novo',
    tokenType: 'Bearer',
    expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
    user,
  };
}

/** Grava uma sessão antes de o `AuthService` ser criado, como se a aba tivesse sido recarregada. */
export function seedSession(token = 'token-salvo', expiresInMs = 15 * 60_000): void {
  sessionStorage.setItem(
    SESSION_STORAGE_KEY,
    JSON.stringify({
      accessToken: token,
      expiresAt: new Date(Date.now() + expiresInMs).toISOString(),
    }),
  );
}

export function problem(status: number, code: string, detail: string, extra: object = {}) {
  return { type: `urn:problem-type:${code}`, title: code, status, detail, code, ...extra };
}

export const PROBLEM_HEADERS = { 'Content-Type': 'application/problem+json' };
