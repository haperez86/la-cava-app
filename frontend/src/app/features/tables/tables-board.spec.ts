import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { storeTestSession } from '../../core/auth/testing';
import { formatCop } from '../../shared/pipes/cop-currency.pipe';
import { Product } from '../menu/product.model';
import { CATEGORIES_URL, PRODUCTS_URL } from '../menu/products.service';
import { Order } from '../orders/order.model';
import { ORDERS_URL, WAITERS_URL } from '../orders/orders.service';
import { EMPTY_TABLE_ACCOUNT } from './table-account-dialog';
import { TablesBoard } from './tables-board';
import { DiningTable, TableAccountResponse } from './tables.model';
import { TABLES_URL, tableAccountUrl } from './tables.service';
import { freeTable, occupiedTable } from './testing';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

const TABLES: DiningTable[] = [
  occupiedTable(13, 3, 'READY', 2, 54000, 'Mateo Ruiz'),
  freeTable(12, 2, false),
  freeTable(11, 1),
];

const MALBEC: Product = {
  id: 3,
  name: 'Copa de Malbec',
  categoryId: 1,
  categoryName: 'Vinos',
  price: 18000,
  description: null,
  icon: '🍷',
  minStock: 0,
  active: true,
  seasonName: null,
  seasonUntil: null,
  available: 5,
  state: 'ON_MENU',
  orderable: true,
  blockReason: null,
};

function order(id: number, quantity: number): Order {
  return {
    id,
    tableId: 13,
    tableNumber: 3,
    waiterId: 7,
    waiterName: 'Mateo Ruiz',
    status: 'READY',
    note: null,
    createdAt: '2026-11-27T20:00:00Z',
    items: [
      { id: id * 10, productId: 3, productName: 'Copa de Malbec', unitPrice: 18000, quantity, note: null, subtotal: 18000 * quantity },
    ],
    total: 18000 * quantity,
  };
}

function account(orders: Order[]): TableAccountResponse {
  return { id: 13, number: 3, account: { orders, total: orders.reduce((sum, o) => sum + o.total, 0) } };
}

describe('TablesBoard (cashier)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<TablesBoard>;
  let root: HTMLElement;

  beforeEach(() => {
    localStorage.clear();
    storeTestSession('CASHIER');
    TestBed.configureTestingModule({
      imports: [TablesBoard],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  async function refresh(): Promise<void> {
    await settle();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(TablesBoard);
    root = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    http.expectOne(TABLES_URL).flush(TABLES);
    await refresh();
  }

  const q = (testId: string) => root.querySelector<HTMLElement>(`[data-testid="${testId}"]`);
  const tiles = () => Array.from(root.querySelectorAll<HTMLButtonElement>('[data-testid="board-table"]'));

  async function openTable3(orders: Order[] = [order(20, 2), order(21, 1)]): Promise<void> {
    await render();
    tiles()[1].click();
    await refresh();
    http.expectOne(tableAccountUrl(13)).flush(account(orders));
    await refresh();
  }

  async function openNewOrder(): Promise<void> {
    q('account-new-order')!.click();
    await refresh();
    http.expectOne((r) => r.url === PRODUCTS_URL).flush({ content: [MALBEC], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    http.expectOne(CATEGORIES_URL).flush([{ id: 1, name: 'Vinos' }]);
    http.expectOne(WAITERS_URL).flush([{ id: 7, fullName: 'Mateo Ruiz' }]);
    await refresh();
  }

  it('CA1 shows the active tables in order with their state, stage, orders, total and waiter', async () => {
    await render();

    expect(tiles().map((t) => t.querySelector('.num')?.textContent?.trim())).toEqual(['Mesa 1', 'Mesa 3']);
    const [free, busy] = tiles();
    expect(free.querySelector('[data-testid="table-status"]')?.textContent?.trim()).toBe('Disponible');
    expect(free.querySelector('[data-testid="table-stage"]')).toBeNull();
    expect(busy.querySelector('[data-testid="table-status"]')?.textContent?.trim()).toBe('Ocupada');
    expect(busy.querySelector('[data-testid="table-stage"]')?.textContent?.trim()).toBe('Listo para recoger');
    expect(busy.querySelector('[data-testid="table-orders"]')?.textContent?.trim()).toBe('2 pedidos');
    expect(busy.querySelector('[data-testid="table-total"]')?.textContent?.trim()).toBe(formatCop(54000));
    expect(busy.querySelector('[data-testid="table-waiter"]')?.textContent?.trim()).toBe('Mateo Ruiz');
  });

  it('CA1 the table button is named by its card content (stage, orders, total, waiter), not replaced by an aria-label', async () => {
    await render();
    const busy = tiles()[1];

    expect(busy.hasAttribute('aria-label')).toBe(false);
    expect(busy.hasAttribute('aria-labelledby')).toBe(false);
    const name = busy.textContent!.replace(/\s+/g, ' ');
    expect(name).toContain('Mesa 3');
    expect(name).toContain('Ocupada');
    expect(name).toContain('Listo para recoger');
    expect(name).toContain('2 pedidos');
    expect(name).toContain(formatCop(54000));
    expect(name).toContain('Mateo Ruiz');
    expect(root.querySelector(`#${busy.getAttribute('aria-describedby')}`)?.textContent?.trim()).toBe('Abre la cuenta de la mesa');
  });

  it('CA2 the state legend is visible', async () => {
    await render();

    expect(q('table-legend')).not.toBeNull();
    expect(root.querySelectorAll('[data-testid="legend-item"]')).toHaveLength(6);
  });

  it('tapping a table opens its account with the open orders, items and total', async () => {
    await openTable3();

    expect(q('account-dialog')?.querySelector('h3')?.textContent).toBe('Mesa 3');
    expect(q('account-status')?.textContent?.trim()).toBe('Ocupada');
    expect(q('account-summary')?.textContent?.trim()).toBe('2 pedidos abiertos');
    const orders = Array.from(root.querySelectorAll('[data-testid="account-order"]'));
    expect(orders).toHaveLength(2);
    expect(orders[0].textContent).toContain('Pedido #20');
    expect(orders[0].textContent).toContain('2 × Copa de Malbec');
    expect(q('account-total')?.textContent).toContain(formatCop(54000));
  });

  it('a free table opens its empty account', async () => {
    await render();
    tiles()[0].click();
    await refresh();
    http.expectOne(tableAccountUrl(11)).flush({ id: 11, number: 1, account: { orders: [], total: 0 } });
    await refresh();

    expect(q('account-status')?.textContent?.trim()).toBe('Disponible');
    expect(q('account-empty')?.textContent?.trim()).toBe(EMPTY_TABLE_ACCOUNT);
    expect(q('account-total')).toBeNull();
  });

  it('"Nuevo pedido" opens the order dialog in staff mode with the table fixed (only the waiter is chosen)', async () => {
    await openTable3();
    await openNewOrder();

    http.expectNone(TABLES_URL);
    expect(q('account-dialog')).toBeNull();
    expect(q('order-dialog')).not.toBeNull();
    expect(q('order-eyebrow')?.textContent?.trim()).toBe('Mesa 3');
    expect(root.querySelector('#order-table')).toBeNull();
    expect(root.querySelector('#order-waiter')).not.toBeNull();
  });

  it('after creating the order the account and the grid are refreshed', async () => {
    await openTable3([order(20, 2)]);
    await openNewOrder();

    const waiter = root.querySelector<HTMLSelectElement>('#order-waiter')!;
    waiter.value = '7';
    waiter.dispatchEvent(new Event('input'));
    waiter.dispatchEvent(new Event('change'));
    root.querySelector<HTMLButtonElement>('[data-product-id="3"] [data-testid="plus"]')!.click();
    await refresh();
    q('send-order')!.click();
    await refresh();

    const req = http.expectOne(ORDERS_URL);
    expect(req.request.body).toEqual({ tableId: 13, waiterId: 7, items: [{ productId: 3, quantity: 1 }] });
    req.flush(order(21, 1));
    await refresh();

    http.expectOne(TABLES_URL).flush([occupiedTable(13, 3, 'PENDING', 2, 54000), freeTable(11, 1)]);
    http.expectOne(tableAccountUrl(13)).flush(account([order(20, 2), order(21, 1)]));
    await refresh();

    expect(q('order-dialog')).toBeNull();
    expect(root.querySelectorAll('[data-testid="account-order"]')).toHaveLength(2);
    expect(tiles()[1].querySelector('[data-testid="table-stage"]')?.textContent?.trim()).toBe('Pendiente');
  });

  it('closing the account returns to the grid', async () => {
    await openTable3();
    q('account-close')!.click();
    await refresh();

    expect(q('account-dialog')).toBeNull();
    expect(tiles()).toHaveLength(2);
  });
});
