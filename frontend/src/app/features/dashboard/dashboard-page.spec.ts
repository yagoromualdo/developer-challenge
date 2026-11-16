import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PROBLEM_HEADERS, problem } from '../../core/auth/auth.testing';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { requestIdInterceptor } from '../../core/http/request-id.interceptor';
import { DashboardPage } from './dashboard-page';
import { DashboardSummary } from './dashboard.models';

const SUMMARY_URL = '/api/dashboard/summary';

function summary(overrides: Partial<DashboardSummary> = {}): DashboardSummary {
  return {
    month: '2026-11',
    requestsByStatus: {
      DRAFT: 2,
      PENDING_APPROVAL: 3,
      CHANGES_REQUESTED: 0,
      APPROVED: 1,
      REJECTED: 0,
      CANCELLED: 0,
    },
    totalRequests: 6,
    budgets: [
      {
        costCenterId: 1,
        costCenterCode: 'CC-TI',
        costCenterName: 'Tecnologia',
        amount: '50000.00',
        consumed: '12500.50',
        available: '37499.50',
      },
    ],
    ...overrides,
  };
}

// O Intl separa "R$" do número com espaço não separável.
function text(element: Element | null | undefined): string {
  return (element?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

describe('DashboardPage', () => {
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([requestIdInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function render(): Promise<{ page: HTMLElement; stable: () => Promise<HTMLElement> }> {
    const fixture = TestBed.createComponent(DashboardPage);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    return {
      page,
      stable: async () => {
        await fixture.whenStable();
        return page;
      },
    };
  }

  it('shows loading and then the totals by status and the budget of the month', async () => {
    const { page, stable } = await render();
    expect(page.querySelector('[role=status]')?.textContent).toContain('Carregando');

    httpTesting.expectOne(SUMMARY_URL).flush(summary());
    await stable();

    expect(page.querySelector('[role=status]')).toBeNull();
    expect(text(page.querySelector('.subtitle'))).toBe('Competência: novembro de 2026');
    expect(text(page.querySelector('[data-testid=total-requests]'))).toBe('Total: 6');

    const pending = page.querySelector('[data-status=PENDING_APPROVAL]');
    expect(text(pending?.querySelector('.count'))).toBe('3');
    expect(text(pending?.querySelector('.label'))).toBe('Aguardando aprovação');
    expect(pending?.getAttribute('href')).toBe('/requests?status=PENDING_APPROVAL');
    expect(page.querySelectorAll('[data-status]')).toHaveLength(6);

    const budget = page.querySelector('[data-cost-center="1"]');
    expect(text(budget?.querySelector('mat-card-title'))).toBe('CC-TI · Tecnologia');
    expect(text(budget?.querySelector('[data-testid=amount]'))).toBe('R$ 50.000,00');
    expect(text(budget?.querySelector('[data-testid=consumed]'))).toBe('R$ 12.500,50');
    expect(text(budget?.querySelector('[data-testid=available]'))).toBe('R$ 37.499,50');
    expect(text(budget)).toContain('25% consumido');
  });

  it('shows the empty states when there are no requests nor cost centers in scope', async () => {
    const { stable } = await render();
    httpTesting.expectOne(SUMMARY_URL).flush(
      summary({
        requestsByStatus: { DRAFT: 0, PENDING_APPROVAL: 0 },
        totalRequests: 0,
        budgets: [],
      }),
    );
    const page = await stable();

    expect(text(page.querySelector('[data-testid=requests-empty]'))).toContain(
      'Nenhuma solicitação encontrada.',
    );
    expect(page.querySelector('[data-status]')).toBeNull();
    expect(text(page.querySelector('[data-testid=budgets-empty]'))).toBe(
      'Nenhum centro de custo no seu escopo.',
    );
  });

  it('says when the month has no budget instead of drawing the bar', async () => {
    const { stable } = await render();
    httpTesting.expectOne(SUMMARY_URL).flush(
      summary({
        budgets: [
          {
            costCenterId: 2,
            costCenterCode: 'CC-RH',
            costCenterName: 'Recursos Humanos',
            amount: '0.00',
            consumed: '0.00',
            available: '0.00',
          },
        ],
      }),
    );
    const page = await stable();

    const budget = page.querySelector('[data-cost-center="2"]');
    expect(text(budget)).toContain('Sem orçamento definido para o mês.');
    expect(budget?.querySelector('mat-progress-bar')).toBeNull();
  });

  it('shows the error with the support code and loads again on retry', async () => {
    const { stable } = await render();
    httpTesting
      .expectOne(SUMMARY_URL)
      .flush(problem(500, 'INTERNAL_ERROR', 'Falha interna.', { requestId: 'req-42' }), {
        status: 500,
        statusText: 'Internal Server Error',
        headers: PROBLEM_HEADERS,
      });
    let page = await stable();

    const alert = page.querySelector('[role=alert]');
    expect(text(alert)).toContain('Não foi possível carregar o dashboard.');
    expect(text(alert)).toContain('Código para o suporte: req-42');

    const retry = Array.from(page.querySelectorAll('button')).find(
      (button) => text(button) === 'Tentar novamente',
    );
    retry?.click();
    await stable();

    httpTesting.expectOne(SUMMARY_URL).flush(summary());
    page = await stable();
    expect(page.querySelector('[role=alert]')).toBeNull();
    expect(text(page.querySelector('[data-testid=total-requests]'))).toBe('Total: 6');
  });
});
