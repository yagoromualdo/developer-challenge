import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { PROBLEM_HEADERS, problem } from '../../core/auth/auth.testing';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { requestIdInterceptor } from '../../core/http/request-id.interceptor';
import { provideAppLocale } from '../../core/locale';
import { RequestFormPage } from './request-form-page';
import { CostCenter, PurchaseRequestDetail } from './requests.models';

@Component({ template: '' })
class DetailStub {}

const COST_CENTERS: CostCenter[] = [
  { id: 1, code: 'CC-TI', name: 'Tecnologia', createdAt: '2026-01-01T00:00:00Z' },
];

function detail(overrides: Partial<PurchaseRequestDetail> = {}): PurchaseRequestDetail {
  return {
    id: 42,
    ownerId: 1,
    costCenterId: 1,
    status: 'DRAFT',
    version: 1,
    lockVersion: 3,
    title: 'Notebooks',
    description: 'Troca dos notebooks do time',
    category: 'EQUIPMENT',
    neededBy: null,
    total: '9799.80',
    submittedAt: null,
    competenceMonth: null,
    createdAt: '2026-11-10T15:00:00Z',
    updatedAt: '2026-11-10T15:00:00Z',
    items: [{ description: 'Notebook', quantity: 2, unitPrice: '4899.90', subtotal: '9799.80' }],
    steps: [],
    allowedActions: ['EDIT', 'SUBMIT', 'CANCEL'],
    ...overrides,
  };
}

function text(element: Element | null | undefined): string {
  return (element?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

describe('RequestFormPage', () => {
  let httpTesting: HttpTestingController;
  let router: Router;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideAppLocale(),
        provideRouter([
          { path: 'requests/new', component: RequestFormPage },
          { path: 'requests/:id/edit', component: RequestFormPage },
          { path: 'requests/:id', component: DetailStub },
        ]),
        provideHttpClient(withInterceptors([requestIdInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(TestBed.inject(MatSnackBar), 'open').mockReturnValue(undefined as never);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => httpTesting.verify());

  async function screen(): Promise<HTMLElement> {
    await harness.fixture.whenStable();
    return harness.fixture.nativeElement as HTMLElement;
  }

  async function openCreate(): Promise<HTMLElement> {
    await harness.navigateByUrl('/requests/new');
    httpTesting.expectOne('/api/cost-centers').flush(COST_CENTERS);
    return screen();
  }

  async function openEdit(current: PurchaseRequestDetail = detail()): Promise<HTMLElement> {
    await harness.navigateByUrl(`/requests/${current.id}/edit`);
    httpTesting.expectOne('/api/cost-centers').flush(COST_CENTERS);
    httpTesting.expectOne(`/api/requests/${current.id}`).flush(current);
    return screen();
  }

  function button(root: HTMLElement, label: string): HTMLButtonElement {
    const found = Array.from(root.querySelectorAll('button')).find((b) => text(b) === label);
    if (!found) {
      throw new Error(`botão "${label}" não encontrado`);
    }
    return found;
  }

  function field(root: HTMLElement, selector: string): HTMLInputElement {
    const input = root.querySelector<HTMLInputElement>(selector);
    if (!input) {
      throw new Error(`campo ${selector} não encontrado`);
    }
    return input;
  }

  function type(root: HTMLElement, selector: string, value: string): void {
    const input = field(root, selector);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function itemField(root: HTMLElement, index: number, name: string): string {
    return `[data-item-index="${index}"] [formcontrolname=${name}]`;
  }

  function fieldError(root: HTMLElement, selector: string): string {
    return text(field(root, selector).closest('mat-form-field')?.querySelector('mat-error'));
  }

  async function chooseCategory(root: HTMLElement, label: string): Promise<void> {
    root.querySelector<HTMLElement>('[data-testid=category] .mat-mdc-select-trigger')?.click();
    await screen();
    const option = Array.from(document.querySelectorAll('mat-option')).find(
      (candidate) => text(candidate) === label,
    );
    (option as HTMLElement | undefined)?.click();
    await screen();
  }

  async function fillValidRequest(root: HTMLElement): Promise<void> {
    type(root, 'input[name=title]', '  Notebooks  ');
    type(root, 'textarea[name=description]', 'Troca dos notebooks do time');
    await chooseCategory(root, 'Equipamentos');
    type(root, itemField(root, 0, 'description'), 'Notebook');
    type(root, itemField(root, 0, 'quantity'), '2');
    type(root, itemField(root, 0, 'unitPrice'), '4899,9');
    await screen();
  }

  function expectCreate(): TestRequest {
    return httpTesting.expectOne((req) => req.method === 'POST' && req.url === '/api/requests');
  }

  it('recalculates the displayed total when items are added, changed and removed', async () => {
    const view = await openCreate();
    expect(text(view.querySelector('[data-testid=total]'))).toBe('R$ 0,00');

    type(view, itemField(view, 0, 'quantity'), '2');
    type(view, itemField(view, 0, 'unitPrice'), '4899,90');
    await screen();
    expect(text(view.querySelector('[data-testid=total]'))).toBe('R$ 9.799,80');

    button(view, 'Adicionar item').click();
    await screen();
    type(view, itemField(view, 1, 'quantity'), '3');
    type(view, itemField(view, 1, 'unitPrice'), '0.10');
    await screen();
    expect(text(view.querySelectorAll('[data-testid=item-subtotal]')[1])).toBe('R$ 0,30');
    expect(text(view.querySelector('[data-testid=total]'))).toBe('R$ 9.800,10');

    view.querySelector<HTMLButtonElement>('[aria-label="Remover o item 1"]')?.click();
    await screen();
    expect(view.querySelectorAll('[data-item-index]').length).toBe(1);
    expect(text(view.querySelector('[data-testid=total]'))).toBe('R$ 0,30');
  });

  it('creates the draft with an Idempotency-Key and opens the created request', async () => {
    const view = await openCreate();
    await fillValidRequest(view);
    button(view, 'Criar rascunho').click();
    await screen();

    const request = expectCreate();
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(request.request.body).toEqual({
      title: 'Notebooks',
      description: 'Troca dos notebooks do time',
      category: 'EQUIPMENT',
      costCenterId: null,
      neededBy: null,
      items: [{ description: 'Notebook', quantity: 2, unitPrice: '4899.90' }],
    });
    expect(button(view, 'Salvando…').disabled).toBe(true);

    request.flush(detail(), { status: 201, statusText: 'Created' });
    await vi.waitFor(() => expect(router.url).toBe('/requests/42'));
  });

  it('reuses the same Idempotency-Key when the same form is sent again', async () => {
    const view = await openCreate();
    await fillValidRequest(view);

    const submit = button(view, 'Criar rascunho');
    submit.click();
    submit.click();
    await screen();
    const first = expectCreate();
    const key = first.request.headers.get('Idempotency-Key');
    first.flush('', { status: 503, statusText: 'Service Unavailable' });
    await screen();

    button(view, 'Criar rascunho').click();
    await screen();
    const retry = expectCreate();
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush(detail(), { status: 201, statusText: 'Created' });
    await vi.waitFor(() => expect(router.url).toBe('/requests/42'));

    const freshForm = await openCreate();
    await fillValidRequest(freshForm);
    button(freshForm, 'Criar rascunho').click();
    await screen();
    const other = expectCreate();
    expect(other.request.headers.get('Idempotency-Key')).not.toBe(key);
    other.flush(detail({ id: 43 }), { status: 201, statusText: 'Created' });
  });

  it('shows each 400 error from the server on its field, including FormArray items', async () => {
    const view = await openCreate();
    await fillValidRequest(view);
    button(view, 'Adicionar item').click();
    await screen();
    type(view, itemField(view, 1, 'description'), 'Mouse');
    type(view, itemField(view, 1, 'quantity'), '1');
    type(view, itemField(view, 1, 'unitPrice'), '89,90');
    button(view, 'Criar rascunho').click();
    await screen();

    expectCreate().flush(
      problem(400, 'VALIDATION_FAILED', 'A requisição contém campos inválidos.', {
        errors: [
          { field: 'items[1].quantity', message: 'deve ser no máximo 1000000' },
          { field: 'neededBy', message: 'deve ser uma data futura' },
          { field: 'Idempotency-Key', message: 'deve ter de 1 a 255 caracteres ASCII visíveis' },
        ],
      }),
      { status: 400, statusText: 'Bad Request', headers: PROBLEM_HEADERS },
    );
    await screen();

    expect(fieldError(view, itemField(view, 1, 'quantity'))).toBe('Deve ser no máximo 1000000');
    expect(fieldError(view, itemField(view, 0, 'quantity'))).toBe('');
    expect(fieldError(view, 'input[name=neededBy]')).toBe('Deve ser uma data futura');
    const summary = text(view.querySelector('.field-summary'));
    expect(summary).toContain('Corrija os campos destacados.');
    expect(summary).toContain('Idempotency-Key: deve ter de 1 a 255 caracteres ASCII visíveis');
    expect(field(view, itemField(view, 1, 'quantity')).getAttribute('aria-invalid')).toBe('true');

    type(view, itemField(view, 1, 'quantity'), '2');
    await screen();
    expect(fieldError(view, itemField(view, 1, 'quantity'))).toBe('');
  });

  it('keeps an invalid form local instead of sending it', async () => {
    const view = await openCreate();
    type(view, itemField(view, 0, 'unitPrice'), '10,999');
    button(view, 'Criar rascunho').click();
    await screen();

    expect(fieldError(view, 'input[name=title]')).toBe('Campo obrigatório.');
    expect(fieldError(view, itemField(view, 0, 'unitPrice'))).toContain('2 casas decimais');
    httpTesting.expectNone((req) => req.method === 'POST');
  });

  it('explains a reused Idempotency-Key and shows business rule details from the API', async () => {
    const view = await openCreate();
    await fillValidRequest(view);
    button(view, 'Criar rascunho').click();
    await screen();
    expectCreate().flush(
      problem(409, 'IDEMPOTENCY_KEY_REUSED', 'A Idempotency-Key informada já foi usada.'),
      { status: 409, statusText: 'Conflict', headers: PROBLEM_HEADERS },
    );
    await screen();
    expect(text(view.querySelector('[data-testid=notice]'))).toContain(
      'Este formulário já criou uma solicitação com outros dados',
    );

    button(view, 'Criar rascunho').click();
    await screen();
    expectCreate().flush(
      problem(422, 'COST_CENTER_REQUIRED', 'Seu usuário não pertence a um centro de custo.'),
      { status: 422, statusText: 'Unprocessable Entity', headers: PROBLEM_HEADERS },
    );
    await screen();
    expect(view.querySelector('[data-testid=notice]')).toBeNull();
    expect(text(view.querySelector('app-problem-alert'))).toContain(
      'Seu usuário não pertence a um centro de custo.',
    );
  });

  it('edits the current version sending the lockVersion and no Idempotency-Key', async () => {
    const view = await openEdit();
    expect(field(view, 'input[name=title]').value).toBe('Notebooks');
    expect(field(view, itemField(view, 0, 'unitPrice')).value).toBe('4899,90');
    expect(field(view, 'input[name=costCenterLabel]').value).toBe('CC-TI · Tecnologia');
    expect(text(view.querySelector('[data-testid=total]'))).toBe('R$ 9.799,80');

    type(view, itemField(view, 0, 'quantity'), '3');
    button(view, 'Salvar alterações').click();
    await screen();

    const put = httpTesting.expectOne('/api/requests/42');
    expect(put.request.method).toBe('PUT');
    expect(put.request.headers.has('Idempotency-Key')).toBe(false);
    expect(put.request.body).toMatchObject({
      lockVersion: 3,
      items: [{ description: 'Notebook', quantity: 3, unitPrice: '4899.90' }],
    });
    expect(put.request.body).not.toHaveProperty('costCenterId');
    put.flush(detail({ lockVersion: 4 }));
    await vi.waitFor(() => expect(router.url).toBe('/requests/42'));
  });

  it('on 409 while editing shows the conflict message and reloads the request', async () => {
    const view = await openEdit();
    button(view, 'Salvar alterações').click();
    await screen();
    httpTesting
      .expectOne((req) => req.method === 'PUT')
      .flush(problem(409, 'CONCURRENT_MODIFICATION', 'O recurso foi alterado.'), {
        status: 409,
        statusText: 'Conflict',
        headers: PROBLEM_HEADERS,
      });
    await screen();

    expect(text(view.querySelector('[data-testid=notice]'))).toBe(
      'Esta solicitação foi alterada por outra pessoa. Os dados foram atualizados.',
    );
    httpTesting
      .expectOne((req) => req.method === 'GET' && req.url === '/api/requests/42')
      .flush(detail({ lockVersion: 5, title: 'Notebooks novos' }));
    await screen();
    expect(field(view, 'input[name=title]').value).toBe('Notebooks novos');

    button(view, 'Salvar alterações').click();
    await screen();
    const retry = httpTesting.expectOne((req) => req.method === 'PUT');
    expect(retry.request.body).toMatchObject({ lockVersion: 5 });
    retry.flush(detail({ lockVersion: 6 }));
  });

  it('does not show the form when the API does not allow editing', async () => {
    const view = await openEdit(detail({ status: 'PENDING_APPROVAL', allowedActions: [] }));

    expect(text(view.querySelector('[data-testid=not-editable]'))).toContain(
      'Esta solicitação não pode ser editada no momento.',
    );
    expect(view.querySelector('form')).toBeNull();
  });
});
