import { CopCurrencyPipe, formatCop } from './cop-currency.pipe';

describe('CopCurrencyPipe', () => {
  it('formats whole Colombian pesos without decimals', () => {
    expect(formatCop(70000)).toBe('$ 70.000');
    expect(formatCop('1250000.00')).toBe('$ 1.250.000');
    expect(new CopCurrencyPipe().transform(null)).toBe('$ 0');
  });
});
