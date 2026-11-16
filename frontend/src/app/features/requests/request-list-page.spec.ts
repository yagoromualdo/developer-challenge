import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { PROBLEM_HEADERS, problem } from '../../core/auth/auth.testing';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { Page } from '../../core/http/page';
import { requestIdInterceptor } from '../../core/http/request-id.interceptor';
import { provideAppLocale } from '../../core/locale';
import { RequestListPage } from './request-list-page';
import { CostCenter, PurchaseRequestSummary } from './requests.models';

const COST_CENTERS: CostCenter[] = [
  { id: 1, code: 'CC-TI', name: 'Tecnologia', createdAt: '2026-01-01T00:00:00Z' },
  { id: 2, code: 'CC-RH', name: 'Recursos Humanos', createdAt: '2026-01-01T00:00:00Z' },
];

function summary(overrides: Partial<PurchaseRequestSummary> = {}): PurchaseRequestSummary {
  return {
    id: 7,
    ownerId: 3,
    costCenterId: 2,
    status: 'PENDING_APPROVAL',
    version: 1,
    title: 'Notebooks para o time',
    category: 'EQUIPMENT',
    total: '9799.80',
    neededBy: null,
    submittedAt: '2026-11-10T15:00:00Z',
    createdAt: '2026-11-10T15:00:00Z',
    updatedAt: '2026-11-11T15:00:00Z',
    ...overrides,
  };
}

function page(
  data: PurchaseRequestSummary[],
  overrides: Partial<Page<PurchaseRequestSummary>> = {},
): Page<PurchaseRequestSummary> {
  return { data, page: 0, size: 20, totalElements: data.length, totalPages: 1, ...overrides };
}

function text(element: Element | null | undefined): string {
  return (element?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

describe('RequestListPage', () => {
  let httpTesting: HttpTestingController;
  let router: Router;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideAppLocale(),
        provideRouter([{ path: 'requests', component: RequestListPage }]),
        provideHttpClient(withInterceptors([requestIdInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => httpTesting.verify());

  async function open(url: string): Promise<HTMLElement> {
    await harness.navigateByUrl(url);
    httpTesting.expectOne('/api/cost-centers').flush(COST_CENTERS);
    return screen();
  }

  async function screen(): Promise<HTMLElement> {
    await harness.fixture.whenStable();
    return harness.fixture.nativeElement as HTMLElement;
  }

  function expectListRequest(): TestRequest {
    return httpTesting.expectOne((req) => req.url === '/api/requests');
  }

  function button(root: HTMLElement, label: string): HTMLButtonElement {
    const found = Array.from(root.querySelectorAll('button')).find((b) => text(b) === label);
    if (!found) {
      throw new Error(`botão "${label}" não encontrado`);
    }
    return found;
  }

  function type(root: HTMLElement, name: string, value: string): void {
    const input = root.querySelector<HTMLInputElement>(`input[name=${name}]`);
    if (!input) {
      throw new Error(`campo ${name} não encontrado`);
    }
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('reproduces the same server search when a shared URL is opened', async () => {
    const view = await open(
      '/requests?status=APPROVED&status=PENDING_APPROVAL&category=SOFTWARE&costCenterId=2' +
        '&createdFrom=2026-11-01&createdTo=2026-11-30&sort=total,asc&page=1&size=10',
    );

    const request = expectListRequest();
    const params = request.request.params;
    expect(params.getAll('status')).toEqual(['PENDING_APPROVAL', 'APPROVED']);
    expect(params.getAll('category')).toEqual(['SOFTWARE']);
    expect(params.get('costCenterId')).toBe('2');
    expect(params.get('createdFrom')).toBe('2026-11-01');
    expect(params.get('createdTo')).toBe('2026-11-30');
    expect(params.get('sort')).toBe('total,asc');
    expect(params.get('page')).toBe('1');
    expect(params.get('size')).toBe('10');

    request.flush(page([summary()], { page: 1, size: 10, totalElements: 15, totalPages: 2 }));
    await screen();

    const row = view.querySelector('[data-request-id="7"]');
    expect(text(row?.querySelector('a'))).toBe('Notebooks para o time');
    expect(row?.querySelector('a')?.getAttribute('href')).toBe('/requests/7');
    expect(text(row)).toContain('Aguardando aprovação');
    expect(text(row)).toContain('Equipamentos');
    expect(text(row)).toContain('CC-RH');
    expect(text(row)).toContain('R$ 9.799,80');
    expect(text(row)).toContain('10/11/2026');

    expect(text(view.querySelector('[data-testid=filter-status]'))).toContain(
      'Aguardando aprovação, Aprovada',
    );
    expect(text(view.querySelector('[data-testid=filter-cost-center]'))).toContain('CC-RH');
    expect(view.querySelector<HTMLInputElement>('input[name=createdFrom]')?.value).toBe(
      '2026-11-01',
    );
    expect(view.querySelector('th[aria-sort=ascending]')?.textContent).toContain('Total');
    expect(text(view.querySelector('.mat-mdc-paginator-range-label'))).toBe('11 – 15 de 15');
    expect(router.url).toContain('status=PENDING_APPROVAL&status=APPROVED');
  });

  it('applies the filters by navigating to a new URL and searching from the first page', async () => {
    const view = await open('/requests?page=3&sort=title,asc');
    expectListRequest().flush(page([summary()], { page: 3, totalElements: 80, totalPages: 4 }));
    await screen();

    type(view, 'createdFrom', '2026-11-01');
    button(view, 'Filtrar').click();
    await screen();

    expect(router.url).toBe('/requests?createdFrom=2026-11-01&sort=title,asc');
    const request = expectListRequest();
    expect(request.request.params.get('createdFrom')).toBe('2026-11-01');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('sort')).toBe('title,asc');
    request.flush(page([]));
    await screen();

    expect(text(view.querySelector('[data-testid=list-empty]'))).toContain(
      'Nenhuma solicitação encontrada com esses filtros.',
    );
    button(view, 'Limpar filtros').click();
    await screen();
    expect(router.url).toBe('/requests?sort=title,asc');
    expectListRequest().flush(page([summary()]));
  });

  it('keeps an inverted date range in the form instead of searching', async () => {
    const view = await open('/requests');
    expectListRequest().flush(page([summary()]));
    await screen();

    type(view, 'createdFrom', '2026-11-20');
    type(view, 'createdTo', '2026-11-10');
    button(view, 'Filtrar').click();
    await screen();

    expect(text(view.querySelector('mat-error'))).toBe(
      'Deve ser igual ou posterior à data inicial.',
    );
    expect(router.url).toBe('/requests');
    httpTesting.expectNone((req) => req.url === '/api/requests');
  });

  it('sorts and paginates on the server through the URL', async () => {
    const view = await open('/requests');
    expectListRequest().flush(page([summary()], { totalElements: 45, totalPages: 3 }));
    await screen();

    const totalHeader = Array.from(view.querySelectorAll('th')).find((th) =>
      text(th).includes('Total'),
    );
    totalHeader?.querySelector<HTMLElement>('.mat-sort-header-container')?.click();
    await screen();
    expect(router.url).toBe('/requests?sort=total,asc');
    expectListRequest().flush(page([summary()], { totalElements: 45, totalPages: 3 }));
    await screen();

    view.querySelector<HTMLButtonElement>('.mat-mdc-paginator-navigation-next')?.click();
    await screen();
    expect(router.url).toBe('/requests?page=1&sort=total,asc');
    const request = expectListRequest();
    expect(request.request.params.get('page')).toBe('1');
    request.flush(page([summary()], { page: 1, totalElements: 45, totalPages: 3 }));
  });

  it('replaces an invalid URL with the search that was actually made', async () => {
    await open('/requests?status=UNKNOWN&page=-1&size=999&sort=password,desc&foo=bar');

    const request = expectListRequest();
    expect(request.request.params.has('status')).toBe(false);
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.get('sort')).toBe('createdAt,desc');
    request.flush(page([]));
    await screen();

    expect(router.url).toBe('/requests');
  });

  it('shows the error with the field problems from the API and searches again on retry', async () => {
    const view = await open('/requests?costCenterId=99');
    expectListRequest().flush(
      problem(400, 'VALIDATION_FAILED', 'A requisição contém campos inválidos.', {
        requestId: 'req-7',
        errors: [{ field: 'costCenterId', message: 'centro de custo inexistente' }],
      }),
      { status: 400, statusText: 'Bad Request', headers: PROBLEM_HEADERS },
    );
    await screen();

    const alert = view.querySelector('[role=alert]');
    expect(text(alert)).toContain('A requisição contém campos inválidos.');
    expect(text(alert)).toContain('costCenterId: centro de custo inexistente');
    expect(text(alert)).toContain('Código para o suporte: req-7');

    button(view, 'Tentar novamente').click();
    await screen();
    const retry = expectListRequest();
    expect(retry.request.params.get('costCenterId')).toBe('99');
    retry.flush(page([]));
    await screen();
    expect(view.querySelector('[role=alert]')).toBeNull();
  });
});
