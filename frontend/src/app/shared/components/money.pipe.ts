import { Pipe, PipeTransform } from '@angular/core';

const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/** Formata em BRL os valores que a API envia como string decimal (`"4899.90"`). */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatMoney(value);
  }
}

export function formatMoney(value: string | null | undefined): string {
  if (value === null || value === undefined || value.trim() === '') {
    return '';
  }
  const amount = Number(value);
  return Number.isFinite(amount) ? BRL.format(amount) : '';
}
