import { Pipe, PipeTransform } from '@angular/core';

const COP = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
});

/** Pesos colombianos sin decimales: 70000 → "$ 70.000". */
export function formatCop(value: number | string | null | undefined): string {
  const n = typeof value === 'string' ? Number(value) : (value ?? 0);
  return COP.format(Number.isFinite(n) ? n : 0).replace(/ /g, ' ');
}

@Pipe({ name: 'cop' })
export class CopCurrencyPipe implements PipeTransform {
  transform(value: number | string | null | undefined): string {
    return formatCop(value);
  }
}
