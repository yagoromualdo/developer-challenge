import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { PROBLEM_HEADERS, SESSION_STORAGE_KEY, problem, seedSession } from '../auth/auth.testing';
import { ApiError } from './api-problem';
import { authInterceptor } from './auth.interceptor';
import { errorInterceptor } from './error.interceptor';
import { requestIdInterceptor } from './request-id.interceptor';

@Component({ template: '' })
class Blank {}

describe('HTTP interceptors', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    sessionStorage.clear();
    seedSession('token-salvo');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'requests', component: Blank },
          { path: 'login', component: Blank },
        ]),
        provideHttpClient(
          withInterceptors([authInterceptor, requestIdInterceptor, errorInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    await router.navigateByUrl('/requests?status=DRAFT');
  });

  afterEach(() => httpTesting.verify());

  it('sends the bearer token and a request id only to the API', () => {
    http.get('/api/requests').subscribe();
    http.get('https://cdn.example.com/file.json').subscribe();

    const api = httpTesting.expectOne('/api/requests').request;
    expect(api.headers.get('Authorization')).toBe('Bearer token-salvo');
    expect(api.headers.get('X-Request-Id')).toMatch(/^[0-9a-f-]{36}$/);

    const external = httpTesting.expectOne('https://cdn.example.com/file.json').request;
    expect(external.headers.has('Authorization')).toBe(false);
    expect(external.headers.has('X-Request-Id')).toBe(false);

    httpTesting.match(() => true).forEach((req) => req.flush({}));
  });

  it('turns Problem Details into a typed ApiError', async () => {
    const result = firstValueFrom(http.post('/api/requests/1/approve', {}));
    httpTesting
      .expectOne('/api/requests/1/approve')
      .flush(problem(422, 'BUDGET_EXCEEDED', 'Saldo insuficiente.', { requestId: 'req-1' }), {
        status: 422,
        statusText: 'Unprocessable Entity',
        headers: PROBLEM_HEADERS,
      });

    const error = await result.catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 422, code: 'BUDGET_EXCEEDED', requestId: 'req-1' });
    expect((error as ApiError).problem?.detail).toBe('Saldo insuficiente.');
  });

  it('on 401 clears the session and goes to login keeping the current route', async () => {
    const result = firstValueFrom(http.get('/api/requests'));
    httpTesting.expectOne('/api/requests').flush(problem(401, 'UNAUTHORIZED', 'Token expirado.'), {
      status: 401,
      statusText: 'Unauthorized',
      headers: PROBLEM_HEADERS,
    });
    await result.catch(() => undefined);
    await vi.waitFor(() => expect(router.url).toMatch(/^\/login/));

    expect(router.parseUrl(router.url).queryParams).toEqual({
      returnUrl: '/requests?status=DRAFT',
      expired: 'true',
    });
    expect(TestBed.inject(AuthService).accessToken).toBeNull();
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('does not expire the session when the login itself answers 401', async () => {
    const auth = TestBed.inject(AuthService);
    const result = firstValueFrom(auth.login({ email: 'ana@example.com', password: 'errada' }));
    httpTesting
      .expectOne('/api/auth/login')
      .flush(problem(401, 'INVALID_CREDENTIALS', 'E-mail ou senha inválidos.'), {
        status: 401,
        statusText: 'Unauthorized',
        headers: PROBLEM_HEADERS,
      });

    await expect(result).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' });
    expect(router.url).toBe('/requests?status=DRAFT');
    expect(auth.accessToken).toBe('token-salvo');
  });

  it('shows a generic message with the request id on server errors', async () => {
    const open = vi.spyOn(TestBed.inject(MatSnackBar), 'open').mockReturnValue(undefined as never);
    const result = firstValueFrom(http.get('/api/dashboard/summary'));
    const req = httpTesting.expectOne('/api/dashboard/summary');
    const sentId = req.request.headers.get('X-Request-Id');
    req.flush('', { status: 503, statusText: 'Service Unavailable' });

    await expect(result).rejects.toMatchObject({ status: 503, requestId: sentId });
    expect(open).toHaveBeenCalledWith(expect.stringContaining(`${sentId}`), 'Fechar', {
      duration: 8000,
    });
  });
});
