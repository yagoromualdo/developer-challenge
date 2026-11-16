import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { PROBLEM_HEADERS, problem } from '../../core/auth/auth.testing';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { requestIdInterceptor } from '../../core/http/request-id.interceptor';
import { provideAppLocale } from '../../core/locale';
import { ApprovalInboxPage } from './approval-inbox-page';
import { ApprovalInboxItem } from './approvals.models';

@Component({ template: '' })
class DetailStub {}

function item(overrides: Partial<ApprovalInboxItem> = {}): ApprovalInboxItem {
  return {
    id: 42,
    ownerId: 7,
    costCenterId: 1,
    status: 'PENDING_APPROVAL',
    version: 1,
    lockVersion: 5,
    title: 'Notebooks',
    category: 'EQUIPMENT',
    total: '9799.80',
    neededBy: null,
    currentStepLevel: 'NIVEL_1',
    submittedAt: '2026-11-10T15:00:00Z',
    createdAt: '2026-11-01T15:00:00Z',
    updatedAt: '2026-11-10T15:00:00Z',
    ...overrides,
  };
}

const NOTEBOOKS = item();
const LICENSES = item({
  id: 43,
  lockVersion: 2,
  title: 'Licenças',
  category: 'SOFTWARE',
  total: '1200.00',
  currentStepLevel: 'NIVEL_2',
  costCenterId: 2,
});

function text(element: Element | null | undefined): string {
  return (element?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

describe('ApprovalInboxPage', () => {
  let httpTesting: HttpTestingController;
  let snackBarOpen: ReturnType<typeof vi.fn>;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideAppLocale(),
        // Sem animação o modal fecha na hora; no jsdom o fim da animação nunca dispara.
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        provideRouter([
          { path: 'approvals/inbox', component: ApprovalInboxPage },
          { path: 'requests/:id', component: DetailStub },
        ]),
        provideHttpClient(withInterceptors([requestIdInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    snackBarOpen = vi.fn();
    vi.spyOn(TestBed.inject(MatSnackBar), 'open').mockImplementation(
      snackBarOpen as unknown as MatSnackBar['open'],
    );
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    document.querySelectorAll('.cdk-overlay-container').forEach((node) => (node.innerHTML = ''));
    httpTesting.verify();
  });

  async function screen(): Promise<HTMLElement> {
    await harness.fixture.whenStable();
    return harness.fixture.nativeElement as HTMLElement;
  }

  function expectInbox(): TestRequest {
    return httpTesting.expectOne(
      (req) => req.method === 'GET' && req.url === '/api/approvals/inbox',
    );
  }

  async function open(items: ApprovalInboxItem[] = [NOTEBOOKS, LICENSES]): Promise<HTMLElement> {
    await harness.navigateByUrl('/approvals/inbox');
    httpTesting.expectOne('/api/cost-centers').flush([
      { id: 1, code: 'CC-TI', name: 'Tecnologia', createdAt: '2026-01-01T00:00:00Z' },
      { id: 2, code: 'CC-FIN', name: 'Financeiro', createdAt: '2026-01-01T00:00:00Z' },
    ]);
    expectInbox().flush(items);
    return screen();
  }

  function rowTitles(root: HTMLElement): string[] {
    return Array.from(root.querySelectorAll('tr[data-request-id] td:first-child')).map(text);
  }

  function row(root: HTMLElement, id: number): HTMLElement {
    const found = root.querySelector<HTMLElement>(`tr[data-request-id="${id}"]`);
    if (!found) {
      throw new Error(`linha da solicitação ${id} não encontrada`);
    }
    return found;
  }

  function button(root: ParentNode, label: string): HTMLButtonElement {
    const found = Array.from(root.querySelectorAll('button')).find((b) => text(b) === label);
    if (!found) {
      throw new Error(`botão "${label}" não encontrado`);
    }
    return found;
  }

  function dialog(): HTMLElement {
    const found = document.querySelector<HTMLElement>('mat-dialog-container');
    if (!found) {
      throw new Error('modal não está aberto');
    }
    return found;
  }

  async function approveFromInbox(root: HTMLElement, id: number): Promise<TestRequest> {
    button(row(root, id), 'Aprovar').click();
    await screen();
    button(dialog(), 'Aprovar').click();
    await screen();
    return httpTesting.expectOne(
      (req) => req.method === 'POST' && req.url === `/api/requests/${id}/approve`,
    );
  }

  it('mostra exatamente o que a API devolve, com etapa, centro de custo e total', async () => {
    const root = await open();

    expect(rowTitles(root)).toEqual(['Notebooks', 'Licenças']);
    expect(text(root.querySelector('[data-testid=inbox-count]'))).toBe(
      '2 solicitações aguardando sua aprovação.',
    );
    const licenses = text(row(root, 43));
    expect(licenses).toContain('Nível 2');
    expect(licenses).toContain('CC-FIN');
    expect(licenses).toContain('R$ 1.200,00');
    const openLink = Array.from(row(root, 42).querySelectorAll('a')).find(
      (a) => text(a) === 'Abrir',
    );
    expect(openLink?.getAttribute('href')).toBe('/requests/42');
  });

  it('caixa vazia mostra o estado vazio', async () => {
    const root = await open([]);

    expect(text(root.querySelector('[data-testid=inbox-empty]'))).toContain(
      'Nenhuma solicitação aguardando sua aprovação.',
    );
    expect(root.querySelector('table')).toBeNull();
  });

  it('aprovar pela caixa envia o lockVersion do item e recarrega a lista', async () => {
    const root = await open();

    const approve = await approveFromInbox(root, 43);
    expect(approve.request.body).toEqual({ lockVersion: 2 });
    approve.flush({});
    await screen();

    expectInbox().flush([NOTEBOOKS]);
    await screen();

    expect(document.querySelector('mat-dialog-container')).toBeNull();
    expect(rowTitles(root)).toEqual(['Notebooks']);
    expect(snackBarOpen).toHaveBeenCalledWith(
      'Etapa aprovada: Licenças.',
      'Fechar',
      expect.anything(),
    );
  });

  it('fechar o modal sem confirmar não chama a API', async () => {
    const root = await open();

    button(row(root, 42), 'Aprovar').click();
    await screen();
    button(dialog(), 'Voltar').click();
    await screen();

    httpTesting.expectNone((req) => req.method === 'POST');
    expect(rowTitles(root)).toEqual(['Notebooks', 'Licenças']);
  });

  it('409 mostra a mensagem de conflito e recarrega a caixa', async () => {
    const root = await open();

    (await approveFromInbox(root, 42)).flush(
      problem(409, 'CONCURRENT_MODIFICATION', 'A solicitação foi alterada.'),
      { status: 409, statusText: 'Conflict', headers: PROBLEM_HEADERS },
    );
    await screen();
    // Outro aprovador decidiu a etapa: ela sai da caixa.
    expectInbox().flush([LICENSES]);
    await screen();

    expect(text(root.querySelector('[data-testid=notice]'))).toBe(
      'Esta solicitação foi alterada por outra pessoa. Os dados foram atualizados.',
    );
    expect(rowTitles(root)).toEqual(['Licenças']);
    expect(root.querySelector('[data-testid=action-error]')).toBeNull();
  });

  it('422 exibe o detail da API e mantém o item na caixa recarregada', async () => {
    const root = await open();
    const apiDetail =
      'Não é possível aprovar: o orçamento disponível do centro de custo é R$ 1.000,00.';

    (await approveFromInbox(root, 42)).flush(problem(422, 'BUDGET_EXCEEDED', apiDetail), {
      status: 422,
      statusText: 'Unprocessable Content',
      headers: PROBLEM_HEADERS,
    });
    await screen();
    expectInbox().flush([NOTEBOOKS, LICENSES]);
    await screen();

    expect(text(root.querySelector('[data-testid=action-error]'))).toContain(apiDetail);
    expect(root.querySelector('[data-testid=notice]')).toBeNull();
    expect(rowTitles(root)).toEqual(['Notebooks', 'Licenças']);
  });

  it('erro ao carregar mostra o alerta e permite tentar de novo', async () => {
    await harness.navigateByUrl('/approvals/inbox');
    httpTesting.expectOne('/api/cost-centers').flush([]);
    expectInbox().flush(null, { status: 500, statusText: 'Server Error' });
    const root = await screen();

    expect(text(root.querySelector('[role=alert]'))).toContain(
      'Não foi possível carregar a caixa de aprovação.',
    );

    button(root, 'Tentar novamente').click();
    await screen();
    expectInbox().flush([NOTEBOOKS]);
    await screen();

    expect(root.querySelector('[role=alert]')).toBeNull();
    expect(rowTitles(root)).toEqual(['Notebooks']);
  });
});
