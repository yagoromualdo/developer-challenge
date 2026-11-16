import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { Role } from './core/auth/auth.models';
import {
  PROBLEM_HEADERS,
  loginResponse,
  problem,
  seedSession,
  userWith,
} from './core/auth/auth.testing';
import { authInterceptor } from './core/http/auth.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { requestIdInterceptor } from './core/http/request-id.interceptor';

describe('routes, session and role menu', () => {
  let httpTesting: HttpTestingController;
  let router: Router;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    sessionStorage.clear();
  });

  async function setUp(): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(
          withInterceptors([authInterceptor, requestIdInterceptor, errorInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
  }

  // Estes testes só olham navegação; as cargas do dashboard e da listagem têm specs próprias.
  afterEach(() => {
    httpTesting.match('/api/dashboard/summary');
    httpTesting.match((req) => req.url === '/api/requests');
    httpTesting.match('/api/cost-centers');
    httpTesting.verify();
  });

  // Não espera a navegação: ela fica pendente até o teste responder o /auth/me.
  function startNavigation(url: string): void {
    harness.navigateByUrl(url).catch(() => null);
  }

  async function screen(): Promise<HTMLElement> {
    await harness.fixture.whenStable();
    return harness.fixture.nativeElement as HTMLElement;
  }

  function type(root: HTMLElement, selector: string, value: string): void {
    const input = root.querySelector<HTMLInputElement>(selector);
    if (!input) {
      throw new Error(`campo ${selector} não encontrado`);
    }
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function signInAs(role: Role, url: string): Promise<HTMLElement> {
    seedSession();
    await setUp();
    startNavigation(url);
    const me = await vi.waitFor(() => httpTesting.expectOne('/api/auth/me'));
    me.flush(userWith(role));
    return screen();
  }

  it('sends an anonymous visitor to login with the requested route', async () => {
    await setUp();
    await harness.navigateByUrl('/requests?status=DRAFT');

    expect(router.parseUrl(router.url).queryParams).toEqual({
      returnUrl: '/requests?status=DRAFT',
    });
    expect((await screen()).querySelector('h1')?.textContent).toContain('Entrar');
  });

  it('after the token expires, logs in again and returns to the original route', async () => {
    seedSession('token-vencido-no-back');
    await setUp();
    startNavigation('/requests?status=DRAFT');

    const me = await vi.waitFor(() => httpTesting.expectOne('/api/auth/me'));
    expect(me.request.headers.get('Authorization')).toBe('Bearer token-vencido-no-back');
    me.flush(problem(401, 'UNAUTHORIZED', 'Token expirado.'), {
      status: 401,
      statusText: 'Unauthorized',
      headers: PROBLEM_HEADERS,
    });

    await vi.waitFor(() => expect(router.url).toMatch(/^\/login/));
    let page = await screen();
    expect(page.textContent).toContain('Sua sessão expirou');

    type(page, 'input[type=email]', 'ana@example.com');
    type(page, 'input[type=password]', 'Senha@123');
    page.querySelector<HTMLButtonElement>('button[type=submit]')?.click();

    const login = httpTesting.expectOne('/api/auth/login');
    expect(login.request.headers.has('Authorization')).toBe(false);
    expect(login.request.body).toEqual({ email: 'ana@example.com', password: 'Senha@123' });
    login.flush(loginResponse(userWith('SOLICITANTE')));

    await vi.waitFor(() => expect(router.url).toBe('/requests?status=DRAFT'));
    page = await screen();
    expect(page.querySelector('[data-testid=current-user]')?.textContent).toContain('Ana Teste');
  });

  it('shows the API message for wrong credentials and stays on login', async () => {
    await setUp();
    await harness.navigateByUrl('/login?returnUrl=%2Frequests');
    const page = await screen();

    type(page, 'input[type=email]', 'ana@example.com');
    type(page, 'input[type=password]', 'errada');
    page.querySelector<HTMLButtonElement>('button[type=submit]')?.click();
    httpTesting
      .expectOne('/api/auth/login')
      .flush(problem(401, 'INVALID_CREDENTIALS', 'E-mail ou senha inválidos.'), {
        status: 401,
        statusText: 'Unauthorized',
        headers: PROBLEM_HEADERS,
      });

    expect((await screen()).querySelector('[role=alert]')?.textContent).toContain(
      'E-mail ou senha inválidos.',
    );
    expect(router.url).toBe('/login?returnUrl=%2Frequests');
  });

  it('ignores a returnUrl that points outside the app', async () => {
    await setUp();
    await harness.navigateByUrl('/login?returnUrl=%2F%2Fevil.example.com');
    const page = await screen();

    type(page, 'input[type=email]', 'ana@example.com');
    type(page, 'input[type=password]', 'Senha@123');
    page.querySelector<HTMLButtonElement>('button[type=submit]')?.click();
    httpTesting.expectOne('/api/auth/login').flush(loginResponse());

    await vi.waitFor(() => expect(router.url).toBe('/dashboard'));
  });

  it('shows the approval inbox in the menu only for approver roles', async () => {
    const approverPage = await signInAs('APROVADOR', '/dashboard');
    expect(approverPage.querySelector('nav')?.textContent).toContain('Caixa de aprovação');
  });

  it('keeps a requester out of the approval inbox', async () => {
    const page = await signInAs('SOLICITANTE', '/approvals/inbox');

    await vi.waitFor(() => expect(router.url).toBe('/dashboard'));
    expect(page.querySelector('nav')?.textContent).not.toContain('Caixa de aprovação');
  });

  it('logs out clearing the session', async () => {
    const page = await signInAs('ADMIN', '/dashboard');
    const logout = Array.from(page.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Sair'),
    );
    logout?.click();

    await vi.waitFor(() => expect(router.url).toBe('/login'));
    expect(sessionStorage.length).toBe(0);
  });
});
