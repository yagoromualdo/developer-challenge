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
import { RequestDetailPage } from './request-detail-page';
import {
  PurchaseRequestDetail,
  RequestEvent,
  RequestVersion,
  RequestVersionSummary,
} from './requests.models';

@Component({ template: '' })
class EditStub {}

const STEP_PENDING = {
  order: 1,
  level: 'NIVEL_1',
  status: 'PENDING',
  decidedBy: null,
  decidedAt: null,
  comment: null,
} as const;

function detail(overrides: Partial<PurchaseRequestDetail> = {}): PurchaseRequestDetail {
  return {
    id: 42,
    ownerId: 7,
    costCenterId: 1,
    status: 'PENDING_APPROVAL',
    version: 2,
    lockVersion: 5,
    title: 'Notebooks',
    description: 'Troca dos notebooks do time',
    category: 'EQUIPMENT',
    neededBy: null,
    total: '9799.80',
    submittedAt: '2026-11-10T15:00:00Z',
    competenceMonth: '2026-11',
    createdAt: '2026-11-01T15:00:00Z',
    updatedAt: '2026-11-10T15:00:00Z',
    items: [{ description: 'Notebook', quantity: 2, unitPrice: '4899.90', subtotal: '9799.80' }],
    steps: [STEP_PENDING],
    allowedActions: ['APPROVE', 'REJECT'],
    ...overrides,
  };
}

const VERSIONS: RequestVersionSummary[] = [
  {
    version: 1,
    current: false,
    title: 'Notebooks',
    category: 'EQUIPMENT',
    total: '4899.90',
    submittedAt: '2026-11-02T15:00:00Z',
    competenceMonth: '2026-11',
    createdAt: '2026-11-01T15:00:00Z',
  },
  {
    version: 2,
    current: true,
    title: 'Notebooks',
    category: 'EQUIPMENT',
    total: '9799.80',
    submittedAt: '2026-11-10T15:00:00Z',
    competenceMonth: '2026-11',
    createdAt: '2026-11-05T15:00:00Z',
  },
];

const HISTORY: RequestEvent[] = [
  {
    id: 1,
    version: 1,
    actorId: 7,
    actorRole: 'SOLICITANTE',
    action: 'CREATE',
    fromStatus: null,
    toStatus: 'DRAFT',
    stepLevel: null,
    comment: null,
    occurredAt: '2026-11-01T15:00:00Z',
    requestId: 'req-1',
  },
  {
    id: 2,
    version: 1,
    actorId: 9,
    actorRole: 'APROVADOR',
    action: 'REQUEST_CHANGES',
    fromStatus: 'PENDING_APPROVAL',
    toStatus: 'CHANGES_REQUESTED',
    stepLevel: 'NIVEL_1',
    comment: 'Falta um notebook para o estagiário',
    occurredAt: '2026-11-03T15:00:00Z',
    requestId: 'req-2',
  },
];

function text(element: Element | null | undefined): string {
  return (element?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

describe('RequestDetailPage', () => {
  let httpTesting: HttpTestingController;
  let snackBar: { open: ReturnType<typeof vi.fn> };
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideAppLocale(),
        // Sem animação o modal fecha na hora; no jsdom o fim da animação nunca dispara.
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        provideRouter([
          { path: 'requests/:id', component: RequestDetailPage },
          { path: 'requests/:id/edit', component: EditStub },
        ]),
        provideHttpClient(withInterceptors([requestIdInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    snackBar = { open: vi.fn() };
    vi.spyOn(TestBed.inject(MatSnackBar), 'open').mockImplementation(
      snackBar.open as unknown as MatSnackBar['open'],
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

  /** Responde as cargas que toda (re)abertura do detalhe dispara. */
  function flushLoad(current: PurchaseRequestDetail): void {
    httpTesting.expectOne(`/api/requests/${current.id}`).flush(current);
    httpTesting.expectOne(`/api/requests/${current.id}/versions`).flush(VERSIONS);
    httpTesting.expectOne(`/api/requests/${current.id}/history`).flush(HISTORY);
  }

  async function open(current: PurchaseRequestDetail = detail()): Promise<HTMLElement> {
    await harness.navigateByUrl(`/requests/${current.id}`);
    httpTesting
      .expectOne('/api/cost-centers')
      .flush([{ id: 1, code: 'CC-TI', name: 'Tecnologia', createdAt: '2026-01-01T00:00:00Z' }]);
    flushLoad(current);
    return screen();
  }

  function actionLabels(root: HTMLElement): string[] {
    return Array.from(root.querySelectorAll('[data-testid=actions] [data-action]')).map(text);
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

  function typeComment(value: string): void {
    const textarea = dialog().querySelector<HTMLTextAreaElement>('textarea[name=comment]');
    if (!textarea) {
      throw new Error('campo de comentário não encontrado');
    }
    textarea.value = value;
    textarea.dispatchEvent(new Event('input'));
  }

  function expectTransition(path: string): TestRequest {
    return httpTesting.expectOne(
      (req) => req.method === 'POST' && req.url === `/api/requests/42/${path}`,
    );
  }

  describe('botões a partir de allowedActions', () => {
    it('mostra só Aprovar e Rejeitar quando a API devolve ["APPROVE","REJECT"]', async () => {
      const root = await open(detail({ allowedActions: ['APPROVE', 'REJECT'] }));

      expect(actionLabels(root)).toEqual(['Aprovar', 'Rejeitar']);
      expect(root.querySelector('[data-testid=no-actions]')).toBeNull();
    });

    it('não mostra nenhum botão quando allowedActions vem vazio', async () => {
      const root = await open(detail({ allowedActions: [] }));

      expect(actionLabels(root)).toEqual([]);
      expect(text(root.querySelector('[data-testid=no-actions]'))).toContain(
        'Nenhuma ação disponível',
      );
    });

    it('não deriva permissão do status: rascunho sem allowedActions não ganha botões', async () => {
      const root = await open(detail({ status: 'DRAFT', steps: [], allowedActions: [] }));

      expect(actionLabels(root)).toEqual([]);
    });

    it('EDIT vira link para o formulário de edição', async () => {
      const root = await open(
        detail({ status: 'DRAFT', steps: [], allowedActions: ['EDIT', 'SUBMIT', 'CANCEL'] }),
      );

      expect(actionLabels(root)).toEqual([
        'Editar',
        'Enviar para aprovação',
        'Cancelar solicitação',
      ]);
      const edit = root.querySelector<HTMLAnchorElement>('[data-action=EDIT]');
      expect(edit?.getAttribute('href')).toBe('/requests/42/edit');
    });
  });

  describe('execução das ações', () => {
    it('envia o lockVersion atual e recarrega o detalhe com os novos allowedActions', async () => {
      const root = await open(
        detail({ status: 'DRAFT', steps: [], allowedActions: ['EDIT', 'SUBMIT', 'CANCEL'] }),
      );

      button(root, 'Enviar para aprovação').click();
      const submit = expectTransition('submit');
      expect(submit.request.body).toEqual({ lockVersion: 5 });
      submit.flush(detail({ lockVersion: 6, allowedActions: [] }));
      await screen();

      flushLoad(detail({ lockVersion: 6, status: 'PENDING_APPROVAL', allowedActions: ['CANCEL'] }));
      await screen();

      expect(actionLabels(root)).toEqual(['Cancelar solicitação']);
      expect(snackBar.open).toHaveBeenCalledWith(
        'Solicitação enviada para aprovação.',
        'Fechar',
        expect.anything(),
      );
    });

    it('rejeitar exige comentário de pelo menos 10 caracteres no modal', async () => {
      const root = await open();

      button(root, 'Rejeitar').click();
      await screen();
      typeComment('   curto   ');
      button(dialog(), 'Rejeitar').click();
      await screen();

      expect(text(dialog().querySelector('mat-error'))).toContain('pelo menos 10 caracteres');
      httpTesting.expectNone((req) => req.url.endsWith('/reject'));

      typeComment('  Fora do orçamento do trimestre  ');
      button(dialog(), 'Rejeitar').click();
      await screen();

      const reject = expectTransition('reject');
      expect(reject.request.body).toEqual({
        lockVersion: 5,
        comment: 'Fora do orçamento do trimestre',
      });
      reject.flush(detail({ status: 'REJECTED', allowedActions: [] }));
      await screen();
      flushLoad(detail({ status: 'REJECTED', lockVersion: 6, allowedActions: [] }));
      await screen();

      expect(document.querySelector('mat-dialog-container')).toBeNull();
      expect(actionLabels(root)).toEqual([]);
    });

    it('fechar o modal sem confirmar não chama a API', async () => {
      const root = await open();

      button(root, 'Aprovar').click();
      await screen();
      button(dialog(), 'Voltar').click();
      await screen();

      httpTesting.expectNone((req) => req.method === 'POST');
      expect(actionLabels(root)).toEqual(['Aprovar', 'Rejeitar']);
    });
  });

  describe('tratamento de 409 e 422', () => {
    it('409 mostra a mensagem de conflito e recarrega o detalhe', async () => {
      const root = await open();

      button(root, 'Aprovar').click();
      await screen();
      button(dialog(), 'Aprovar').click();
      await screen();

      expectTransition('approve').flush(
        problem(409, 'CONCURRENT_MODIFICATION', 'A solicitação foi alterada.'),
        { status: 409, statusText: 'Conflict', headers: PROBLEM_HEADERS },
      );
      await screen();

      // Outro aprovador decidiu a etapa: o detalhe recarregado já não oferece ações.
      flushLoad(detail({ lockVersion: 6, status: 'APPROVED', allowedActions: [] }));
      await screen();

      expect(text(root.querySelector('[data-testid=notice]'))).toBe(
        'Esta solicitação foi alterada por outra pessoa. Os dados foram atualizados.',
      );
      expect(actionLabels(root)).toEqual([]);
      expect(root.querySelector('[data-testid=action-error]')).toBeNull();
    });

    it('422 exibe o detail da API junto das ações', async () => {
      const root = await open();
      const apiDetail =
        'Não é possível aprovar: o orçamento disponível do centro de custo é R$ 1.000,00.';

      button(root, 'Aprovar').click();
      await screen();
      button(dialog(), 'Aprovar').click();
      await screen();

      expectTransition('approve').flush(problem(422, 'BUDGET_EXCEEDED', apiDetail), {
        status: 422,
        statusText: 'Unprocessable Content',
        headers: PROBLEM_HEADERS,
      });
      await screen();
      flushLoad(detail());
      await screen();

      const alert = root.querySelector('[data-testid=action-error]');
      expect(text(alert)).toContain(apiDetail);
      expect(alert?.closest('.actions-panel')).not.toBeNull();
      expect(root.querySelector('[data-testid=notice]')).toBeNull();
    });
  });

  describe('versões e histórico', () => {
    it('mostra a cadeia, o histórico e permite consultar uma versão anterior', async () => {
      const root = await open();

      expect(text(root.querySelector('[data-testid=steps]'))).toContain('Etapa 1 · Nível 1');
      expect(text(root.querySelector('[data-testid=steps]'))).toContain('Pendente');
      const history = text(root.querySelector('[data-testid=history]'));
      expect(history).toContain('Ajustes solicitados · Nível 1');
      expect(history).toContain('Falta um notebook para o estagiário');

      button(root, 'v1 · R$ 4.899,90').click();
      await screen();
      const previous: RequestVersion = {
        requestId: 42,
        version: 1,
        current: false,
        title: 'Notebooks',
        description: 'Primeira versão',
        category: 'EQUIPMENT',
        neededBy: null,
        total: '4899.90',
        submittedAt: '2026-11-02T15:00:00Z',
        competenceMonth: '2026-11',
        createdAt: '2026-11-01T15:00:00Z',
        items: [
          { description: 'Notebook', quantity: 1, unitPrice: '4899.90', subtotal: '4899.90' },
        ],
        steps: [{ ...STEP_PENDING, status: 'CHANGES_REQUESTED', decidedBy: 9 }],
      };
      httpTesting.expectOne('/api/requests/42/versions/1').flush(previous);
      await screen();

      expect(text(root.querySelector('[data-testid=previous-version]'))).toContain(
        'Você está vendo a versão 1',
      );
      expect(text(root.querySelector('[data-testid=total]'))).toBe('R$ 4.899,90');
      expect(text(root.querySelector('[data-testid=steps]'))).toContain('Ajustes solicitados');
      // As ações continuam as da versão atual.
      expect(actionLabels(root)).toEqual(['Aprovar', 'Rejeitar']);

      button(root, 'Ver versão atual').click();
      await screen();
      expect(text(root.querySelector('[data-testid=total]'))).toBe('R$ 9.799,80');
    });

    it('404 da API mostra o erro sem ações', async () => {
      await harness.navigateByUrl('/requests/99');
      httpTesting.expectOne('/api/cost-centers').flush([]);
      httpTesting
        .expectOne('/api/requests/99')
        .flush(problem(404, 'NOT_FOUND', 'Solicitação não encontrada.'), {
          status: 404,
          statusText: 'Not Found',
          headers: PROBLEM_HEADERS,
        });
      httpTesting.expectOne('/api/requests/99/versions').flush([]);
      httpTesting.expectOne('/api/requests/99/history').flush([]);
      const root = await screen();

      expect(text(root.querySelector('[role=alert]'))).toContain('Solicitação não encontrada.');
      expect(root.querySelector('[data-testid=actions]')).toBeNull();
    });
  });
});
