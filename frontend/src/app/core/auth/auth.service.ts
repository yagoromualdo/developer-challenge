import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, map, of, tap } from 'rxjs';
import { API_BASE_URL, SKIP_SESSION_EXPIRED } from '../http/api';
import { CurrentUser, LoginCredentials, LoginResponse, StoredSession } from './auth.models';

const STORAGE_KEY = 'purchase-requests.session';

/**
 * Sessão do usuário: o token fica em `sessionStorage` (ADR 007) e os dados do usuário só em memória,
 * recarregados de `GET /auth/me` quando a aba é recarregada.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<StoredSession | null>(readStoredSession());
  private readonly user = signal<CurrentUser | null>(null);

  readonly currentUser = this.user.asReadonly();
  readonly isLoggedIn = computed(() => this.session() !== null);

  get accessToken(): string | null {
    return this.session()?.accessToken ?? null;
  }

  /** O back ainda pode recusar o token antes do `expiresAt`; nesse caso o 401 encerra a sessão. */
  hasValidSession(now: number = Date.now()): boolean {
    const session = this.session();
    return session !== null && Date.parse(session.expiresAt) > now;
  }

  login(credentials: LoginCredentials): Observable<CurrentUser> {
    return this.http
      .post<LoginResponse>(`${API_BASE_URL}/auth/login`, credentials, {
        context: new HttpContext().set(SKIP_SESSION_EXPIRED, true),
      })
      .pipe(
        tap((response) => {
          this.storeSession({ accessToken: response.accessToken, expiresAt: response.expiresAt });
          this.user.set(response.user);
        }),
        map((response) => response.user),
      );
  }

  ensureCurrentUser(): Observable<CurrentUser> {
    const cached = this.user();
    if (cached) {
      return of(cached);
    }
    return this.http
      .get<CurrentUser>(`${API_BASE_URL}/auth/me`)
      .pipe(tap((user) => this.user.set(user)));
  }

  clearSession(): void {
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // Storage bloqueado: a sessão em memória é limpa mesmo assim.
    }
    this.session.set(null);
    this.user.set(null);
  }

  private storeSession(session: StoredSession): void {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // Storage bloqueado: a sessão vale só até recarregar a página.
    }
    this.session.set(session);
  }
}

function readStoredSession(): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed: unknown = JSON.parse(raw);
    return isStoredSession(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function isStoredSession(value: unknown): value is StoredSession {
  const candidate = value as Partial<StoredSession> | null;
  return (
    typeof candidate?.accessToken === 'string' &&
    typeof candidate.expiresAt === 'string' &&
    !Number.isNaN(Date.parse(candidate.expiresAt))
  );
}
