import { formatMoney } from './money.pipe';

describe('formatMoney', () => {
  it('formats the decimal strings sent by the API as BRL', () => {
    expect(formatMoney('4899.90').replace(/\s/g, ' ')).toBe('R$ 4.899,90');
    expect(formatMoney('0.00').replace(/\s/g, ' ')).toBe('R$ 0,00');
  });

  it('shows nothing for missing or invalid values', () => {
    expect(formatMoney(null)).toBe('');
    expect(formatMoney('')).toBe('');
    expect(formatMoney('abc')).toBe('');
  });
});
