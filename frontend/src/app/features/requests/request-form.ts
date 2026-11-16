import { AbstractControl, FormArray, FormControl, FormGroup, Validators } from '@angular/forms';
import { ApiFieldError } from '../../core/http/api-problem';
import { Category } from '../../shared/request-category';
import {
  CreateRequestPayload,
  PurchaseRequestDetail,
  UpdateRequestPayload,
} from './requests.models';

/** Mesmos limites do `RequestLimits` do back; aqui só evitam uma ida ao servidor que daria 400. */
export const REQUEST_LIMITS = {
  titleMaxLength: 200,
  descriptionMaxLength: 2000,
  itemDescriptionMaxLength: 500,
  maxItems: 100,
  maxItemQuantity: 1_000_000,
} as const;

// Até 9 dígitos inteiros e 2 casas, com vírgula ou ponto: o back recusa mais de 2 casas em vez de arredondar.
const UNIT_PRICE_PATTERN = /^\d{1,9}([.,]\d{1,2})?$/;
const INTEGER_PATTERN = /^\d+$/;

export type ItemForm = FormGroup<{
  description: FormControl<string>;
  quantity: FormControl<number | null>;
  unitPrice: FormControl<string>;
}>;

export type RequestForm = FormGroup<{
  title: FormControl<string>;
  description: FormControl<string>;
  category: FormControl<Category | null>;
  costCenterId: FormControl<number | null>;
  neededBy: FormControl<string>;
  items: FormArray<ItemForm>;
}>;

export type ItemValue = ReturnType<ItemForm['getRawValue']>;

export function buildRequestForm(): RequestForm {
  return new FormGroup({
    title: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(REQUEST_LIMITS.titleMaxLength)],
    }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(REQUEST_LIMITS.descriptionMaxLength)],
    }),
    category: new FormControl<Category | null>(null, Validators.required),
    costCenterId: new FormControl<number | null>(null),
    neededBy: new FormControl('', { nonNullable: true }),
    items: new FormArray<ItemForm>([buildItemForm()], Validators.required),
  });
}

export function buildItemForm(item?: { description: string; quantity: number; unitPrice: string }) {
  return new FormGroup({
    description: new FormControl(item?.description ?? '', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(REQUEST_LIMITS.itemDescriptionMaxLength),
      ],
    }),
    quantity: new FormControl<number | null>(item?.quantity ?? 1, [
      Validators.required,
      Validators.pattern(INTEGER_PATTERN),
      Validators.min(1),
      Validators.max(REQUEST_LIMITS.maxItemQuantity),
    ]),
    unitPrice: new FormControl(item ? item.unitPrice.replace('.', ',') : '', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(UNIT_PRICE_PATTERN), positivePrice],
    }),
  });
}

/** Preenche o formulário com a versão atual para edição; o centro de custo fica fora (não muda). */
export function fillRequestForm(form: RequestForm, detail: PurchaseRequestDetail): void {
  form.reset({
    title: detail.title,
    description: detail.description,
    category: detail.category,
    costCenterId: detail.costCenterId,
    neededBy: detail.neededBy ?? '',
  });
  // Depois do reset: ele zeraria os valores dos itens que não vieram no objeto acima.
  const items = form.controls.items;
  items.clear({ emitEvent: false });
  detail.items.forEach((item) => items.push(buildItemForm(item), { emitEvent: false }));
  items.updateValueAndValidity();
}

function positivePrice(control: AbstractControl<string>) {
  const cents = toCents(control.value);
  return cents !== null && cents <= 0n ? { positive: true } : null;
}

/** Valor em centavos, exato (BigInt): 100 itens × 1.000.000 × 999.999.999,99 passa do limite do `number`. */
function toCents(price: string): bigint | null {
  const normalized = price.trim();
  if (!UNIT_PRICE_PATTERN.test(normalized)) {
    return null;
  }
  const [integer, fraction = ''] = normalized.split(/[.,]/);
  return BigInt(integer) * 100n + BigInt(fraction.padEnd(2, '0'));
}

function centsToDecimal(cents: bigint): string {
  return `${cents / 100n}.${(cents % 100n).toString().padStart(2, '0')}`;
}

/** Subtotal como string decimal (`"4899.90"`); `null` enquanto a linha não tem quantidade e preço válidos. */
export function itemSubtotal(item: ItemValue): string | null {
  const cents = toCents(item.unitPrice);
  const quantity = item.quantity;
  if (cents === null || quantity === null || !Number.isInteger(quantity) || quantity < 1) {
    return null;
  }
  return centsToDecimal(cents * BigInt(quantity));
}

/** Total só para exibição: soma as linhas válidas; o valor que vale é o que o back calcula. */
export function displayTotal(items: ItemValue[]): string {
  const cents = items.reduce((sum, item) => {
    const subtotal = itemSubtotal(item);
    return subtotal === null ? sum : sum + (toCents(subtotal) ?? 0n);
  }, 0n);
  return centsToDecimal(cents);
}

function toItemsPayload(form: RequestForm): CreateRequestPayload['items'] {
  return form.controls.items.getRawValue().map((item) => ({
    description: item.description.trim(),
    quantity: Number(item.quantity),
    unitPrice: centsToDecimal(toCents(item.unitPrice) ?? 0n),
  }));
}

export function toCreatePayload(form: RequestForm): CreateRequestPayload {
  const value = form.getRawValue();
  return {
    title: value.title.trim(),
    description: value.description.trim(),
    category: value.category as Category,
    costCenterId: value.costCenterId,
    neededBy: value.neededBy || null,
    items: toItemsPayload(form),
  };
}

export function toUpdatePayload(form: RequestForm, lockVersion: number): UpdateRequestPayload {
  const { title, description, category, neededBy, items } = toCreatePayload(form);
  return { title, description, category, neededBy, items, lockVersion };
}

/** `items[0].quantity` → `['items', 0, 'quantity']`, o caminho que `AbstractControl.get` entende. */
export function toControlPath(field: string): (string | number)[] {
  return field
    .split(/[.[\]]/)
    .filter((segment) => segment !== '')
    .map((segment) => (INTEGER_PATTERN.test(segment) ? Number(segment) : segment));
}

/**
 * Põe cada mensagem do 400 no controle do campo (`setErrors({ server })`), inclusive nos itens do
 * `FormArray`, e devolve as que não correspondem a nenhum campo da tela (ex.: `lockVersion`).
 */
export function applyServerErrors(form: RequestForm, errors: ApiFieldError[]): ApiFieldError[] {
  const messages = new Map<AbstractControl, string[]>();
  const unmapped: ApiFieldError[] = [];
  for (const error of errors) {
    const control = form.get(toControlPath(error.field));
    if (!control || control === form) {
      unmapped.push(error);
      continue;
    }
    messages.set(control, [...(messages.get(control) ?? []), error.message]);
  }
  // O erro `server` some sozinho na próxima edição do campo, quando os validadores rodam de novo.
  messages.forEach((list, control) => {
    control.setErrors({ ...control.errors, server: list.join('; ') });
    control.markAsTouched();
  });
  return unmapped;
}

/** Mensagem do primeiro erro do controle, local ou vinda do servidor. */
export function controlErrorMessage(control: AbstractControl): string | null {
  const errors = control.errors;
  if (!errors) {
    return null;
  }
  if (typeof errors['server'] === 'string') {
    return capitalize(errors['server']);
  }
  if (errors['required']) {
    return 'Campo obrigatório.';
  }
  if (errors['maxlength']) {
    return `Use no máximo ${errors['maxlength'].requiredLength} caracteres.`;
  }
  if (errors['min']) {
    return `Deve ser no mínimo ${errors['min'].min}.`;
  }
  if (errors['max']) {
    return `Deve ser no máximo ${errors['max'].max.toLocaleString('pt-BR')}.`;
  }
  if (errors['positive']) {
    return 'Deve ser maior que zero.';
  }
  if (errors['pattern']) {
    return errors['pattern'].requiredPattern === String(INTEGER_PATTERN)
      ? 'Use um número inteiro.'
      : 'Use até 9 dígitos e 2 casas decimais (ex.: 1234,56).';
  }
  return 'Valor inválido.';
}

function capitalize(message: string): string {
  return message.charAt(0).toUpperCase() + message.slice(1);
}
