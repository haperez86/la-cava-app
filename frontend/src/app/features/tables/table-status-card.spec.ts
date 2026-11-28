import { ComponentFixture, TestBed } from '@angular/core/testing';
import { formatCop } from '../../shared/pipes/cop-currency.pipe';
import { NO_OPEN_ORDERS } from './table-status';
import { TableStatusCard } from './table-status-card';
import { DiningTable, TableStage } from './tables.model';
import { freeTable, occupiedTable } from './testing';

describe('TableStatusCard', () => {
  let fixture: ComponentFixture<TableStatusCard>;
  let root: HTMLElement;

  async function render(table: DiningTable): Promise<void> {
    fixture = TestBed.createComponent(TableStatusCard);
    fixture.componentRef.setInput('table', table);
    root = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  const q = (testId: string) => root.querySelector<HTMLElement>(`[data-testid="${testId}"]`);

  it('CA1 a FREE table shows "Disponible" without stage, orders, total or waiter', async () => {
    await render(freeTable(1, 7));

    expect(root.querySelector('.num')?.textContent?.trim()).toBe('Mesa 7');
    expect(q('table-status')?.textContent?.trim()).toBe('Disponible');
    expect(q('table-status')?.classList.contains('busy')).toBe(false);
    expect(q('table-stage')).toBeNull();
    expect(q('table-orders')).toBeNull();
    expect(q('table-total')).toBeNull();
    expect(q('table-waiter')).toBeNull();
    expect(q('table-meta')?.textContent?.trim()).toBe(NO_OPEN_ORDERS);
    expect(root.classList.contains('occupied')).toBe(false);
  });

  it.each<[TableStage, string, string]>([
    ['READY', 'Listo para recoger', 'ready'],
    ['IN_PREPARATION', 'En preparación', 'busy'],
    ['PENDING', 'Pendiente', 'busy'],
    ['SERVED', 'Servido', ''],
  ])('CA1 an OCCUPIED table with stage %s shows "Ocupada" and the stage "%s"', async (stage, label, pill) => {
    await render(occupiedTable(2, 4, stage, 3, 54000, 'Mateo Ruiz'));

    expect(q('table-status')?.textContent?.trim()).toBe('Ocupada');
    expect(q('table-status')?.classList.contains('busy')).toBe(true);
    expect(q('table-stage')?.textContent?.trim()).toBe(label);
    if (pill) {
      expect(q('table-stage')?.classList.contains(pill)).toBe(true);
    } else {
      expect(q('table-stage')?.classList.contains('busy')).toBe(false);
      expect(q('table-stage')?.classList.contains('ready')).toBe(false);
    }
    expect(q('table-orders')?.textContent?.trim()).toBe('3 pedidos');
    expect(q('table-total')?.textContent?.trim()).toBe(formatCop(54000));
    expect(q('table-waiter')?.textContent?.trim()).toBe('Mateo Ruiz');
    expect(root.classList.contains('occupied')).toBe(true);
  });

  it('CA1 "Listo para recoger" is highlighted with the ready-order glow; other stages are not', async () => {
    await render(occupiedTable(2, 4, 'READY'));
    expect(q('table-stage')?.classList.contains('glow')).toBe(true);
    expect(root.classList.contains('ready-tile')).toBe(true);

    fixture.componentRef.setInput('table', occupiedTable(2, 4, 'IN_PREPARATION'));
    await fixture.whenStable();
    expect(q('table-stage')?.classList.contains('glow')).toBe(false);
    expect(root.classList.contains('ready-tile')).toBe(false);
  });

  it('CA1 a single open order reads "1 pedido"', async () => {
    await render(occupiedTable(2, 4, 'PENDING', 1, 18000));

    expect(q('table-orders')?.textContent?.trim()).toBe('1 pedido');
    expect(q('table-total')?.textContent?.trim()).toBe(formatCop(18000));
  });

  it('is not interactive by itself', async () => {
    await render(occupiedTable(2, 4, 'READY'));

    expect(root.querySelector('button, a, [role="button"], [tabindex]')).toBeNull();
  });
});
