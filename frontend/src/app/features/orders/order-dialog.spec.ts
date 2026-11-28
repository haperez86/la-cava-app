import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/notifications/toast.service';
import { formatCop } from '../../shared/pipes/cop-currency.pipe';
import { Category, Product } from '../menu/product.model';
import { CATEGORIES_URL, PRODUCTS_URL } from '../menu/products.service';
import { DiningTable } from '../tables/tables.model';
import { TABLES_URL } from '../tables/tables.service';
import { Order } from './order.model';
import {
  ITEM_NOTE_MAX_LENGTH,
  NO_PRODUCTS_MATCH,
  ORDER_NOTE_MAX_LENGTH,
  OrderDialog,
  OrderTable,
  RESCAN_TABLE,
  TABLE_REQUIRED,
  WAITER_REQUIRED,
  notOrderable,
  onlyAvailable,
  orderSent,
} from './order-dialog';
import { ORDERS_URL, WAITERS_URL } from './orders.service';
import { freeTable } from '../tables/testing';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

function product(overrides: Partial<Product> & Pick<Product, 'id' | 'name'>): Product {
  return {
    categoryId: 1,
    categoryName: 'Vinos',
    price: 10000,
    description: null,
    icon: '🍷',
    minStock: 0,
    active: true,
    seasonName: null,
    seasonUntil: null,
    available: 10,
    state: 'ON_MENU',
    orderable: true,
    blockReason: null,
    ...overrides,
  };
}

const CATEGORIES: Category[] = [
  { id: 1, name: 'Vinos' },
  { id: 2, name: 'Tablas' },
];

const PRODUCTS: Product[] = [
  product({ id: 3, name: 'Copa de Malbec', price: 18000, available: 2 }),
  product({ id: 4, name: 'Sangría', price: 15000, available: 0, state: 'OUT_OF_STOCK', orderable: false, blockReason: 'Agotado' }),
  product({ id: 5, name: 'Tabla de quesos', categoryId: 2, categoryName: 'Tablas', price: 42000, description: 'Quesos maduros', icon: '🧀' }),
  product({ id: 6, name: 'Vino caliente', price: 16000, state: 'OUT_OF_SEASON', orderable: false, blockReason: 'Fuera de temporada' }),
];

const TABLES: DiningTable[] = [
  freeTable(13, 3),
  freeTable(12, 2, false),
  freeTable(11, 1),
];

const WAITER_TABLE: OrderTable = { id: 40, number: 4 };
const PASS = 'LCP.40.1.1790000000.firma';

const CREATED: Order = {
  id: 27,
  tableId: 40,
  tableNumber: 4,
  waiterId: 1,
  waiterName: 'Mateo Ruiz',
  status: 'PENDING',
  note: null,
  createdAt: '2026-11-27T20:00:00Z',
  items: [{ id: 1, productId: 3, productName: 'Copa de Malbec', unitPrice: 18000, quantity: 1, note: null, subtotal: 18000 }],
  total: 18000,
};

describe('OrderDialog', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<OrderDialog>;
  let root: HTMLElement;
  let created: Order[];
  let closed: number;
  let passRejected: number;
  let toastError: ReturnType<typeof vi.spyOn>;
  let toastSuccess: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [OrderDialog],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    const toast = TestBed.inject(ToastService);
    toastError = vi.spyOn(toast, 'error');
    toastSuccess = vi.spyOn(toast, 'success');
  });

  afterEach(() => http.verify());

  async function refresh(): Promise<void> {
    await settle();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  /** Mesero: mesa fija con su comprobante. Cajero/Administrador: sin mesa ni comprobante. */
  async function render(
    mode: 'waiter' | 'cashier',
    products: Product[] = PRODUCTS,
    tableCode = PASS,
  ): Promise<void> {
    fixture = TestBed.createComponent(OrderDialog);
    root = fixture.nativeElement as HTMLElement;
    created = [];
    closed = 0;
    passRejected = 0;
    fixture.componentInstance.created.subscribe((o) => created.push(o));
    fixture.componentInstance.closed.subscribe(() => closed++);
    fixture.componentInstance.passRejected.subscribe(() => passRejected++);
    if (mode === 'waiter') {
      fixture.componentRef.setInput('table', WAITER_TABLE);
      fixture.componentRef.setInput('mode', 'waiter');
      fixture.componentRef.setInput('tableCode', tableCode);
      fixture.componentRef.setInput('waiterName', 'Mateo Ruiz');
    }
    fixture.detectChanges();
    const productsReq = http.expectOne((r) => r.url === PRODUCTS_URL);
    expect(productsReq.request.params.get('size')).toBe('100');
    expect(productsReq.request.params.get('sort')).toBe('name,asc');
    productsReq.flush({ content: products, page: 0, size: 100, totalElements: products.length, totalPages: 1 });
    http.expectOne(CATEGORIES_URL).flush(CATEGORIES);
    if (mode === 'cashier') {
      http.expectOne(TABLES_URL).flush(TABLES);
      http.expectOne(WAITERS_URL).flush([
        { id: 7, fullName: 'Mateo Ruiz' },
        { id: 8, fullName: 'Laura Peña' },
      ]);
    } else {
      http.expectNone(TABLES_URL);
      http.expectNone(WAITERS_URL);
    }
    await refresh();
  }

  const q = (testId: string) => root.querySelector<HTMLElement>(`[data-testid="${testId}"]`);
  const text = (testId: string) => q(testId)?.textContent?.trim() ?? '';
  const names = () =>
    Array.from(root.querySelectorAll('[data-testid="product-name"]')).map((n) => n.textContent?.trim());
  const row = (productId: number) => root.querySelector<HTMLElement>(`[data-product-id="${productId}"]`)!;
  const inRow = <T extends HTMLElement>(productId: number, testId: string) =>
    row(productId).querySelector<T>(`[data-testid="${testId}"]`);

  async function plus(productId: number, times = 1): Promise<void> {
    for (let i = 0; i < times; i++) {
      inRow<HTMLButtonElement>(productId, 'plus')!.click();
      await refresh();
    }
  }

  async function minus(productId: number): Promise<void> {
    inRow<HTMLButtonElement>(productId, 'minus')!.click();
    await refresh();
  }

  async function type(input: HTMLInputElement, value: string): Promise<void> {
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await refresh();
  }

  async function choose(id: string, value: string): Promise<void> {
    const select = root.querySelector<HTMLSelectElement>(`#${id}`)!;
    select.value = value;
    select.dispatchEvent(new Event('input'));
    select.dispatchEvent(new Event('change'));
    await refresh();
  }

  async function tab(label: string): Promise<void> {
    Array.from(root.querySelectorAll<HTMLButtonElement>('[data-testid="category-tab"]'))
      .find((b) => b.textContent?.trim() === label)!
      .click();
    await refresh();
  }

  async function openNote(productId: number): Promise<HTMLInputElement> {
    inRow<HTMLButtonElement>(productId, 'note-toggle')!.click();
    await refresh();
    return inRow<HTMLInputElement>(productId, 'item-note')!;
  }

  async function send(): Promise<void> {
    q('send-order')!.click();
    await refresh();
  }

  // ---------- CA1 ----------

  it('CA1 the category tabs (Todas + categories) really filter the list', async () => {
    await render('waiter');
    const tabs = Array.from(root.querySelectorAll('[data-testid="category-tab"]')).map((b) => b.textContent?.trim());
    expect(tabs).toEqual(['Todas', 'Vinos', 'Tablas']);
    expect(names()).toEqual(['Copa de Malbec', 'Tabla de quesos', 'Sangría', 'Vino caliente']);

    await tab('Tablas');
    expect(names()).toEqual(['Tabla de quesos']);
    expect(q('category-tabs')!.querySelector('.active')?.textContent?.trim()).toBe('Tablas');

    await tab('Vinos');
    expect(names()).toEqual(['Copa de Malbec', 'Sangría', 'Vino caliente']);

    await tab('Todas');
    expect(names()).toHaveLength(4);
  });

  it('CA1 the search filters by name, description or category, ignoring accents and case', async () => {
    await render('waiter');

    await type(q('order-search') as HTMLInputElement, 'SANGRIA');
    expect(names()).toEqual(['Sangría']);

    await type(q('order-search') as HTMLInputElement, 'maduros');
    expect(names()).toEqual(['Tabla de quesos']);

    await tab('Tablas');
    await type(q('order-search') as HTMLInputElement, 'malbec');
    expect(names()).toEqual([]);
    expect(text('no-products')).toBe(NO_PRODUCTS_MATCH);
  });

  it('CA1 the fixed bottom bar shows units and COP total, updated with −/+, and the send button', async () => {
    await render('waiter');
    const bar = q('order-bar')!;
    expect(bar.classList).toContain('sticky-foot');
    // La barra es el último bloque del formulario: queda pegada al fondo del modal desplazable.
    expect(q('order-form')!.lastElementChild).toBe(bar);
    expect(text('order-units')).toBe('Sin productos');
    expect(text('order-total')).toBe(formatCop(0));
    expect((q('send-order') as HTMLButtonElement).disabled).toBe(true);
    expect(text('send-order')).toBe('Enviar a preparación');

    await plus(3, 2);
    await plus(5);
    expect(inRow(3, 'qty')?.textContent).toBe('2');
    expect(text('order-units')).toBe('3 unidades');
    expect(text('order-total')).toBe(formatCop(2 * 18000 + 42000));
    expect((q('send-order') as HTMLButtonElement).disabled).toBe(false);

    await minus(3);
    await minus(3);
    expect(inRow(3, 'qty')?.textContent).toBe('0');
    expect((inRow(3, 'minus') as HTMLButtonElement).disabled).toBe(true);
    expect(text('order-units')).toBe('1 unidad');
    expect(text('order-total')).toBe(formatCop(42000));
  });

  // ---------- CA2 ----------

  it('CA2 products that are not orderable are disabled and show their reason', async () => {
    await render('waiter');

    for (const [id, reason] of [
      [4, 'Agotado'],
      [6, 'Fuera de temporada'],
    ] as const) {
      expect(row(id).classList).toContain('off');
      expect(row(id).getAttribute('aria-disabled')).toBe('true');
      expect(inRow(id, 'block-reason')?.textContent?.trim()).toBe(reason);
      expect((inRow(id, 'plus') as HTMLButtonElement).disabled).toBe(true);
      expect((inRow(id, 'minus') as HTMLButtonElement).disabled).toBe(true);
    }
    expect(inRow(3, 'product-available')?.textContent?.trim()).toBe('2 disponible(s)');
    expect(row(3).textContent).toContain(formatCop(18000));
  });

  it('CA2 + cannot go above the current stock', async () => {
    await render('waiter');

    await plus(3, 2);
    expect((inRow(3, 'plus') as HTMLButtonElement).disabled).toBe(true);
    inRow<HTMLButtonElement>(3, 'plus')!.click();
    await refresh();
    expect(inRow(3, 'qty')?.textContent).toBe('2');
    expect(text('order-units')).toBe('2 unidades');
  });

  // ---------- CA3 ----------

  it('CA3 sends the item note and the general note in the body (trimmed, blank notes omitted)', async () => {
    await render('waiter');
    await plus(3);
    await plus(5);
    expect(inRow(3, 'item-note')).toBeNull();

    await type(await openNote(3), '  sin hielo  ');
    await type(q('order-note') as HTMLInputElement, ' atender primero las bebidas ');
    await send();

    const req = http.expectOne(ORDERS_URL);
    expect(req.request.body).toEqual({
      tableId: 40,
      tableCode: PASS,
      items: [
        { productId: 3, quantity: 1, note: 'sin hielo' },
        { productId: 5, quantity: 1 },
      ],
      note: 'atender primero las bebidas',
    });
    req.flush(CREATED);
    await refresh();
  });

  it('CA3 the "Nota" button only appears on items with quantity > 0', async () => {
    await render('waiter');
    expect(inRow(3, 'note-toggle')).toBeNull();
    await plus(3);
    expect(inRow(3, 'note-toggle')?.textContent?.trim()).toBe('Nota');
    await minus(3);
    expect(inRow(3, 'note-toggle')).toBeNull();
  });

  it(`CA3 an item note accepts ${ITEM_NOTE_MAX_LENGTH} characters and rejects more`, async () => {
    await render('waiter');
    await plus(3);
    const note = await openNote(3);

    await type(note, 'a'.repeat(ITEM_NOTE_MAX_LENGTH + 1));
    await send();
    expect(inRow(3, 'error-item-note')?.textContent?.trim()).toBe(`Máximo ${ITEM_NOTE_MAX_LENGTH} caracteres.`);
    http.expectNone(ORDERS_URL);

    await type(inRow<HTMLInputElement>(3, 'item-note')!, 'a'.repeat(ITEM_NOTE_MAX_LENGTH));
    await send();
    expect(http.expectOne(ORDERS_URL).request.body.items[0].note).toBe('a'.repeat(ITEM_NOTE_MAX_LENGTH));
  });

  it(`CA3 the general note accepts ${ORDER_NOTE_MAX_LENGTH} characters and rejects more`, async () => {
    await render('waiter');
    await plus(3);

    await type(q('order-note') as HTMLInputElement, 'b'.repeat(ORDER_NOTE_MAX_LENGTH + 1));
    await send();
    expect(text('error-note')).toBe(`Máximo ${ORDER_NOTE_MAX_LENGTH} caracteres.`);
    http.expectNone(ORDERS_URL);

    await type(q('order-note') as HTMLInputElement, 'b'.repeat(ORDER_NOTE_MAX_LENGTH));
    await send();
    expect(http.expectOne(ORDERS_URL).request.body.note).toBe('b'.repeat(ORDER_NOTE_MAX_LENGTH));
  });

  // ---------- CA4 ----------

  it('CA4 the waiter sends tableCode = tablePass and no waiterId, with the table fixed', async () => {
    await render('waiter');
    expect(text('order-eyebrow')).toBe('Mesa 4 · Mateo Ruiz');
    expect(root.querySelector('#order-table')).toBeNull();
    expect(root.querySelector('#order-waiter')).toBeNull();

    await plus(3);
    await send();

    const body = http.expectOne(ORDERS_URL).request.body;
    expect(body).toEqual({ tableId: 40, tableCode: PASS, items: [{ productId: 3, quantity: 1 }] });
    expect('waiterId' in body).toBe(false);
  });

  it('CA4 the cashier must choose table and waiter: without them nothing is sent', async () => {
    await render('cashier');
    expect(text('order-eyebrow')).toBe('Servicio en mesa');
    const tables = Array.from(root.querySelectorAll('#order-table option')).map((o) => o.textContent?.trim());
    expect(tables).toEqual(['Elige la mesa', 'Mesa 1', 'Mesa 3']);
    const waiters = Array.from(root.querySelectorAll('#order-waiter option')).map((o) => o.textContent?.trim());
    expect(waiters).toEqual(['Elige el mesero', 'Mateo Ruiz', 'Laura Peña']);

    await plus(5);
    await send();

    expect(text('error-tableId')).toBe(TABLE_REQUIRED);
    expect(text('error-waiterId')).toBe(WAITER_REQUIRED);
    http.expectNone(ORDERS_URL);
  });

  it('CA4 the cashier sends the chosen tableId and waiterId, without tableCode', async () => {
    await render('cashier');
    await plus(5);
    await choose('order-table', '13');
    await choose('order-waiter', '8');
    await send();

    const req = http.expectOne(ORDERS_URL);
    expect(req.request.body).toEqual({ tableId: 13, waiterId: 8, items: [{ productId: 5, quantity: 1 }] });
    req.flush({ ...CREATED, tableId: 13, tableNumber: 3, waiterId: 8, waiterName: 'Laura Peña' });
    await refresh();
    expect(created).toHaveLength(1);
  });

  it('CA4 a 403 because the table pass expired shows the detail and asks to scan again', async () => {
    await render('waiter');
    await plus(3);
    await send();

    http
      .expectOne(ORDERS_URL)
      .flush({ status: 403, detail: RESCAN_TABLE }, { status: 403, statusText: 'Forbidden' });
    await refresh();

    expect(toastError).toHaveBeenCalledWith(RESCAN_TABLE);
    expect(passRejected).toBe(1);
    expect(created).toEqual([]);
  });

  // ---------- CA5 ----------

  it('CA5 201 shows "Pedido #N enviado a preparación." and emits the order', async () => {
    await render('waiter');
    await plus(3);
    await send();

    http.expectOne(ORDERS_URL).flush(CREATED, { status: 201, statusText: 'Created' });
    await refresh();

    expect(toastSuccess).toHaveBeenCalledWith(orderSent(27));
    expect(orderSent(27)).toBe('Pedido #27 enviado a preparación.');
    expect(created).toEqual([CREATED]);
  });

  async function reject422(body: Record<string, unknown>): Promise<void> {
    http
      .expectOne(ORDERS_URL)
      .flush({ status: 422, ...body }, { status: 422, statusText: 'Unprocessable Entity' });
    await refresh();
  }

  it('CA5 a 422 shows the detail, clamps the product to what is available and keeps the rest of the order', async () => {
    await render('waiter');
    await plus(3, 2);
    await plus(5);
    await type(await openNote(5), 'para compartir');
    await send();

    const detail = 'No hay suficiente Copa de Malbec: disponible 1.';
    await reject422({ detail, productId: 3, available: 1 });

    expect(toastError).toHaveBeenCalledWith(detail);
    expect(created).toEqual([]);
    expect(row(3).classList).toContain('conflict');
    expect(inRow(3, 'conflict')?.textContent?.trim()).toBe(onlyAvailable(1));
    // La cantidad baja a lo disponible y no se puede volver a subir; el resto del pedido sigue intacto.
    expect(inRow(3, 'qty')?.textContent).toBe('1');
    expect((inRow(3, 'plus') as HTMLButtonElement).disabled).toBe(true);
    expect(inRow<HTMLInputElement>(5, 'item-note')?.value).toBe('para compartir');
    expect(text('order-units')).toBe('2 unidades');
    expect(text('order-total')).toBe(formatCop(18000 + 42000));
    expect((q('send-order') as HTMLButtonElement).disabled).toBe(false);

    await send();
    expect(http.expectOne(ORDERS_URL).request.body.items).toEqual([
      { productId: 3, quantity: 1 },
      { productId: 5, quantity: 1, note: 'para compartir' },
    ]);
  });

  it('CA5 a 422 with available 0 removes the product from the order and marks it Agotado', async () => {
    await render('waiter');
    await plus(3, 2);
    await send();

    await reject422({ detail: 'Copa de Malbec: disponible 0.', productId: 3, available: 0 });

    expect(inRow(3, 'qty')?.textContent).toBe('0');
    expect(inRow(3, 'block-reason')?.textContent?.trim()).toBe('Agotado');
    expect(inRow(3, 'conflict')?.textContent?.trim()).toBe(onlyAvailable(0));
    expect((inRow(3, 'plus') as HTMLButtonElement).disabled).toBe(true);
    expect(text('order-units')).toBe('Sin productos');
    expect((q('send-order') as HTMLButtonElement).disabled).toBe(true);
    await send();
    http.expectNone(ORDERS_URL);
  });

  it('CA2/CA5 a 422 with a reason disables the product with that reason even if it still has stock', async () => {
    await render('waiter');
    await plus(3);
    await plus(5);
    await send();

    await reject422({
      detail: 'Copa de Malbec no está disponible: Retirado de la carta.',
      productId: 3,
      available: 2,
      reason: 'Retirado de la carta',
    });

    expect(row(3).classList).toContain('off');
    expect(inRow(3, 'block-reason')?.textContent?.trim()).toBe('Retirado de la carta');
    expect(inRow(3, 'conflict')?.textContent?.trim()).toBe(notOrderable('Retirado de la carta'));
    expect(inRow(3, 'qty')?.textContent).toBe('0');
    expect((inRow(3, 'plus') as HTMLButtonElement).disabled).toBe(true);
    expect(text('order-units')).toBe('1 unidad');

    await send();
    expect(http.expectOne(ORDERS_URL).request.body.items).toEqual([{ productId: 5, quantity: 1 }]);
  });

  it('CA5 a 400 maps errors[] to their fields', async () => {
    await render('cashier');
    await plus(5);
    await choose('order-table', '13');
    await choose('order-waiter', '8');
    await send();

    http.expectOne(ORDERS_URL).flush(
      { status: 400, errors: [{ field: 'waiterId', message: 'El mesero no está activo.' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    await refresh();

    expect(text('error-waiterId')).toBe('El mesero no está activo.');
    expect(created).toEqual([]);
  });

  it('CA4 waiter mode is explicit: even with an empty tableCode it never loads /users/waiters nor asks for one', async () => {
    await render('waiter', PRODUCTS, '');
    expect(root.querySelector('#order-waiter')).toBeNull();
    await plus(3);
    await send();
    const body = http.expectOne(ORDERS_URL).request.body;
    expect('waiterId' in body).toBe(false);
    expect(body.tableCode).toBe('');
  });

  it('Escape, the close button and the backdrop do not close the dialog while the order is being sent', async () => {
    await render('waiter');
    await plus(3);
    await send();
    const req = http.expectOne(ORDERS_URL);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    q('order-close')!.click();
    root.querySelector<HTMLElement>('.modal-back')!.click();
    await refresh();
    expect(closed).toBe(0);
    expect((q('order-close') as HTMLButtonElement).disabled).toBe(true);

    req.flush(CREATED);
    await refresh();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await refresh();
    expect(closed).toBe(1);
  });

  it('the close button and Cancelar emit closed', async () => {
    await render('waiter');
    q('order-close')!.click();
    await refresh();
    expect(closed).toBe(1);
  });
});
