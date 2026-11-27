import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/notifications/toast.service';
import { businessToday } from '../../shared/dates/business-date';
import { INVENTORY_PAGE_SIZE, InventoryPage, NO_EXPIRED_LOTS, SEARCH_DEBOUNCE_MS, WRITE_OFF_CONFIRM } from './inventory-page';
import { InventoryRow, InventorySummary, Lot, Movement, OutputResult } from './inventory.model';
import {
  CATEGORIES_URL,
  INVENTORY_URL,
  LOTS_URL,
  MOVEMENTS_URL,
  OUTPUTS_URL,
  PRODUCTS_URL,
  productLotsUrl,
  SUMMARY_URL,
  WRITE_OFF_URL,
} from './inventory.service';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

const CREATED: Lot = {
  id: 41,
  productId: 9,
  productName: 'Vino caliente',
  quantityReceived: 12,
  quantityAvailable: 12,
  expiresOn: businessToday(),
  seasonName: 'Navidad',
  receivedAt: '2026-11-27T15:00:00Z',
  status: 'EXPIRING',
};

function row(overrides: Partial<InventoryRow>): InventoryRow {
  return {
    productId: 1,
    name: 'Copa de Malbec',
    icon: '🍷',
    categoryId: 2,
    categoryName: 'Vinos',
    seasonName: null,
    seasonUntil: null,
    available: 12,
    minStock: 4,
    nextExpiresOn: '2026-12-20',
    daysToExpiry: 23,
    expiredUnits: 0,
    state: 'AVAILABLE',
    hasExpired: false,
    ...overrides,
  };
}

const ROWS: InventoryRow[] = [
  row({ productId: 1, name: 'Copa de Malbec', nextExpiresOn: '2026-11-27', daysToExpiry: 0, state: 'EXPIRING' }),
  row({ productId: 2, name: 'Tabla de quesos', nextExpiresOn: '2026-11-28', daysToExpiry: 1, available: 3, state: 'LOW' }),
  row({ productId: 3, name: 'Sangría', nextExpiresOn: '2026-12-02', daysToExpiry: 5, expiredUnits: 4, hasExpired: true }),
  row({
    productId: 4,
    name: 'Vino caliente',
    seasonName: 'Navidad',
    seasonUntil: '2026-12-31',
    available: 0,
    nextExpiresOn: null,
    daysToExpiry: null,
    state: 'OUT',
  }),
  row({ productId: 5, name: 'Ponche', seasonName: 'Halloween', seasonUntil: '2026-10-31', state: 'OUT_OF_SEASON' }),
];

const SUMMARY: InventorySummary = {
  outOfStock: 1,
  lowStock: 2,
  expiring: 3,
  expiredUnits: 4,
  seasons: ['Halloween', 'Navidad'],
};

function pageOf<T>(content: readonly T[], totalElements = content.length) {
  return {
    content,
    page: 0,
    size: INVENTORY_PAGE_SIZE,
    totalElements,
    totalPages: Math.max(1, Math.ceil(totalElements / INVENTORY_PAGE_SIZE)),
  };
}

describe('InventoryPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<InventoryPage>;
  let root: HTMLElement;

  /** Atiende la carga de la tabla y los contadores (inicial o tras `reload()`). */
  async function flushLists(
    rows: readonly InventoryRow[] = ROWS,
    summary: InventorySummary = SUMMARY,
    total?: number,
  ): Promise<void> {
    http.expectOne((r) => r.url === INVENTORY_URL).flush(pageOf(rows, total));
    http.expectOne(SUMMARY_URL).flush(summary);
    await settle();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [InventoryPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InventoryPage);
    root = fixture.nativeElement as HTMLElement;
    http.expectOne(CATEGORIES_URL).flush([
      { id: 2, name: 'Vinos' },
      { id: 7, name: 'Tablas' },
    ]);
    await flushLists();
  });

  afterEach(() => http.verify());

  const texts = (testId: string) =>
    Array.from(root.querySelectorAll<HTMLElement>(`[data-testid="${testId}"]`)).map((e) => e.textContent?.trim());
  const rowsEls = () => Array.from(root.querySelectorAll<HTMLElement>('[data-testid="inventory-row"]'));
  const toastsOf = () => TestBed.inject(ToastService).toasts().map((t) => [t.kind, t.message]);
  const button = (testId: string) => root.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)!;

  it('shows the header and no dialog until something is opened', () => {
    expect(root.querySelector('h2')?.textContent?.trim()).toBe('Inventario');
    expect(root.querySelector('[role="dialog"]')).toBeNull();
  });

  // ------------------------------------------------------------------ HU-06 CA1 · contadores

  it('CA1 paints the four counters from the summary', () => {
    const value = (key: string) =>
      root.querySelector(`[data-testid="stat-${key}"] [data-testid="stat-value"]`)?.textContent?.trim();
    expect(value('out')).toBe('1');
    expect(value('low')).toBe('2');
    expect(value('expiring')).toBe('3');
    expect(value('expired')).toBe('4');
    expect(root.querySelector('[data-testid="stat-out"]')?.textContent).toContain('Agotados');
    expect(root.querySelector('[data-testid="stat-low"]')?.textContent).toContain('Stock bajo');
    expect(root.querySelector('[data-testid="stat-expiring"]')?.textContent).toContain('Por vencer (≤ 3 días)');
    expect(root.querySelector('[data-testid="stat-expired"]')?.textContent).toContain('Unidades vencidas');
    expect(root.querySelector('[data-testid="write-off-expired-card"]')).not.toBeNull();
  });

  it('CA1 without expired units the expired card hides its write-off button', async () => {
    fixture.componentInstance.reload();
    await flushLists(ROWS, { ...SUMMARY, expiredUnits: 0 });
    expect(root.querySelector('[data-testid="write-off-expired-card"]')).toBeNull();
    expect(root.querySelector('[data-testid="stat-expired"]')?.textContent).toContain('Sin vencidos');
  });

  it.each([
    ['out', 'OUT'],
    ['low', 'LOW'],
    ['expiring', 'EXPIRING'],
    ['expired', 'HAS_EXPIRED'],
  ])('CA1 clicking the %s counter filters by state %s from page 0', async (key, state) => {
    button(`stat-filter-${key}`).click();
    const request = http.expectOne((r) => r.url === INVENTORY_URL);
    expect(request.request.params.get('state')).toBe(state);
    expect(request.request.params.get('page')).toBe('0');
    request.flush(pageOf([]));
    await settle();
    await fixture.whenStable();
    expect(root.querySelector<HTMLSelectElement>('#inv-state')!.value).toBe(state);
    expect(button(`stat-filter-${key}`).getAttribute('aria-pressed')).toBe('true');
  });

  it('CA1 the counter write-off button opens the same confirmation', async () => {
    button('write-off-expired-card').click();
    await fixture.whenStable();
    expect(root.querySelector('[role="alertdialog"]')?.textContent).toContain(WRITE_OFF_CONFIRM);
    button('confirm-cancel').click();
    await fixture.whenStable();
    http.expectNone(WRITE_OFF_URL);
  });

  // ------------------------------------------------------------------ HU-06 CA2 · tabla

  it('CA2 the table shows season, available, minimum, next expiry, expired units and state', () => {
    const headers = Array.from(root.querySelectorAll('th')).map((th) => th.textContent?.trim());
    expect(headers).toEqual(['Producto', 'Temporada', 'Vigente', 'Mínimo', 'Próximo vencimiento', 'Vencidas', 'Estado']);
    expect(texts('row-name')).toEqual(['Copa de Malbec', 'Tabla de quesos', 'Sangría', 'Vino caliente', 'Ponche']);
    expect(rowsEls()[0].textContent).toContain('🍷');
    expect(rowsEls()[3].textContent).toContain('Navidad');
    expect(rowsEls()[3].textContent).toContain('hasta 31/12/2026');
    expect(texts('row-available')).toEqual(['12', '3', '12', '0', '12']);
    expect(rowsEls()[1].querySelector('[data-label="Mínimo"]')?.textContent?.trim()).toBe('4');
    expect(rowsEls()[0].textContent).toContain('27/11/2026');
    expect(texts('row-expired')).toEqual(['0', '0', '4', '0', '0']);
  });

  it('CA2 next expiry is worded from daysToExpiry: 0 / 1 / N / null', () => {
    expect(texts('row-expiry')).toEqual(['vence hoy', 'vence mañana', 'en 5 días', '—', 'en 23 días']);
  });

  it('CA2 each state has its pill text and rows with expired units carry the "Con vencidos" mark', () => {
    expect(texts('row-state')).toEqual(['Por vencer', 'Stock bajo', 'Disponible', 'Agotado', 'Fuera de temporada']);
    const tones = Array.from(root.querySelectorAll<HTMLElement>('[data-testid="row-state"]')).map((p) =>
      p.className.replace('pill', '').trim(),
    );
    expect(tones).toEqual(['busy', 'busy', '', 'danger', 'season']);
    const marks = rowsEls().map((r) => r.querySelector('[data-testid="row-has-expired"]')?.textContent?.trim() ?? null);
    expect(marks).toEqual([null, null, 'Con vencidos', null, null]);
  });

  it('CA2 the pagination asks for the next page and an empty page says so', async () => {
    fixture.componentInstance.reload();
    await flushLists(ROWS, SUMMARY, 25);
    button('pagination-next').click();
    const request = http.expectOne((r) => r.url === INVENTORY_URL);
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe(String(INVENTORY_PAGE_SIZE));
    expect(request.request.params.get('sort')).toBe('name,asc');
    request.flush(pageOf([], 25));
    await settle();
    await fixture.whenStable();
    expect(root.querySelector('[data-testid="inventory-empty"]')?.textContent?.trim()).toBe(
      'No hay productos con ese filtro.',
    );
  });

  // ------------------------------------------------------------------ HU-06 CA3 · filtros

  async function goToPage1(): Promise<void> {
    fixture.componentInstance.reload();
    await flushLists(ROWS, SUMMARY, 25);
    button('pagination-next').click();
    http.expectOne((r) => r.url === INVENTORY_URL).flush(pageOf(ROWS, 25));
    await settle();
    await fixture.whenStable();
  }

  function expectFilter(param: string, value: string): void {
    const request = http.expectOne((r) => r.url === INVENTORY_URL);
    expect(request.request.params.get(param)).toBe(value);
    expect(request.request.params.get('page')).toBe('0');
    request.flush(pageOf(ROWS));
  }

  it('CA3 the text filter waits 250 ms after the last key, then sends q and goes back to page 0', async () => {
    await goToPage1();
    vi.useFakeTimers();
    try {
      const input = root.querySelector<HTMLInputElement>('#inv-search')!;
      for (const value of ['mal', 'malbec']) {
        input.value = value;
        input.dispatchEvent(new Event('input'));
        await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS - 1);
      }
      http.expectNone((r) => r.url === INVENTORY_URL);
      await vi.advanceTimersByTimeAsync(1);
      expectFilter('q', 'malbec');
    } finally {
      vi.useRealTimers();
    }
  });

  it('CA2 after a reload that empties the current page it goes to the last valid page', async () => {
    await goToPage1();
    fixture.componentInstance.reload();
    // La baja encogió la lista a 8 productos: la página 1 llega vacía.
    const stale = http.expectOne((r) => r.url === INVENTORY_URL);
    expect(stale.request.params.get('page')).toBe('1');
    stale.flush(pageOf([], 8));
    http.expectOne(SUMMARY_URL).flush(SUMMARY);
    await settle();
    const retry = http.expectOne((r) => r.url === INVENTORY_URL);
    expect(retry.request.params.get('page')).toBe('0');
    retry.flush(pageOf(ROWS, 8));
    await settle();
    await fixture.whenStable();
    expect(root.querySelector('[data-testid="inventory-empty"]')).toBeNull();
    expect(texts('row-name')).toHaveLength(ROWS.length);
    expect(texts('pagination-summary')).toEqual([`Mostrando 1–8 de 8`]);
  });

  it('CA3 the category filter sends categoryId and goes back to page 0', async () => {
    await goToPage1();
    const select = root.querySelector<HTMLSelectElement>('#inv-category')!;
    expect(Array.from(select.options).map((o) => o.textContent?.trim())).toEqual([
      'Todas las categorías',
      'Vinos',
      'Tablas',
    ]);
    select.value = '7';
    select.dispatchEvent(new Event('change'));
    expectFilter('categoryId', '7');
  });

  it('CA3 the state filter offers every state and sends it from page 0', async () => {
    await goToPage1();
    const select = root.querySelector<HTMLSelectElement>('#inv-state')!;
    expect(Array.from(select.options).map((o) => o.textContent?.trim())).toEqual([
      'Todos los estados',
      'Disponible',
      'Stock bajo',
      'Agotado',
      'Por vencer',
      'Con vencidos',
      'Fuera de temporada',
    ]);
    select.value = 'OUT_OF_SEASON';
    select.dispatchEvent(new Event('change'));
    expectFilter('state', 'OUT_OF_SEASON');
  });

  it('CA3 the season filter lists summary.seasons and sends season from page 0', async () => {
    await goToPage1();
    const select = root.querySelector<HTMLSelectElement>('#inv-season')!;
    expect(Array.from(select.options).map((o) => o.textContent?.trim())).toEqual([
      'Todas las temporadas',
      'Halloween',
      'Navidad',
    ]);
    select.value = 'Navidad';
    select.dispatchEvent(new Event('change'));
    expectFilter('season', 'Navidad');
  });

  it('CA3 "Todos los estados" sends no state parameter', async () => {
    const select = root.querySelector<HTMLSelectElement>('#inv-state')!;
    select.value = '';
    select.dispatchEvent(new Event('change'));
    const request = http.expectOne((r) => r.url === INVENTORY_URL);
    expect(request.request.params.has('state')).toBe(false);
    request.flush(pageOf(ROWS));
  });

  // ------------------------------------------------------------------ HU-06 CA4 · detalle

  const LOTS: Lot[] = [
    {
      ...CREATED,
      id: 8,
      productId: 3,
      productName: 'Sangría',
      quantityReceived: 10,
      quantityAvailable: 6,
      expiresOn: '2026-12-02',
      seasonName: null,
      status: 'ACTIVE',
    },
    {
      ...CREATED,
      id: 5,
      productId: 3,
      productName: 'Sangría',
      quantityReceived: 4,
      quantityAvailable: 4,
      expiresOn: '2026-11-20',
      seasonName: 'Halloween',
      status: 'EXPIRED',
    },
    {
      ...CREATED,
      id: 2,
      productId: 3,
      productName: 'Sangría',
      quantityReceived: 5,
      quantityAvailable: 0,
      expiresOn: '2026-11-10',
      seasonName: null,
      status: 'CONSUMED',
    },
  ];

  const move = (id: number, type: Movement['type'], quantity: number): Movement => ({
    id,
    createdAt: '2026-11-27T15:30:00Z',
    productId: 3,
    productName: 'Sangría',
    lotId: 8,
    type,
    quantity,
    note: type === 'WASTE_OUT' ? 'Se cayó una jarra' : null,
    orderId: null,
    userName: 'Admin',
  });

  const MOVES: Movement[] = [
    move(1, 'LOT_IN', 10),
    move(2, 'SALE', -2),
    move(3, 'ORDER_CANCEL_RETURN', 1),
    move(4, 'WASTE_OUT', -1),
    move(5, 'DAMAGED_OUT', -1),
    move(6, 'INTERNAL_USE_OUT', -1),
    move(7, 'ADJUSTMENT_OUT', -1),
    move(8, 'EXPIRED_WRITE_OFF', -4),
  ];

  async function openSangria(lots: readonly Lot[] = LOTS, moves: readonly Movement[] = MOVES): Promise<void> {
    rowsEls()[2].click();
    await fixture.whenStable();
    http.expectOne(productLotsUrl(3)).flush(lots);
    const request = http.expectOne((r) => r.url === MOVEMENTS_URL);
    expect(request.request.params.get('productId')).toBe('3');
    expect(request.request.params.get('size')).toBe('10');
    request.flush({ content: moves, page: 0, size: 10, totalElements: moves.length, totalPages: 1 });
    await settle();
    await fixture.whenStable();
  }

  it('CA4 clicking a row opens the detail with its lots (by expiry) and their status pills', async () => {
    await openSangria();
    const detail = root.querySelector('[data-testid="lot-detail"]')!;
    expect(detail.querySelector('h3')?.textContent).toContain('Sangría');
    expect(texts('lot-expires')).toEqual(['10/11/2026', '20/11/2026', '02/12/2026']);
    expect(texts('lot-qty')).toEqual(['0 / 5', '4 / 4', '6 / 10']);
    expect(texts('lot-status')).toEqual(['Consumido', 'Vencido', 'Vigente']);
    expect(detail.textContent).toContain('Halloween');
    expect(detail.textContent).toContain('27/11/2026'); // recibido, día de Bogotá
  });

  it('CA4 the detail lists recent movements with Spanish types, signed quantity, Bogotá time, note and user', async () => {
    await openSangria();
    expect(texts('movement-type')).toEqual([
      'Entrada',
      'Venta',
      'Devolución por cancelación',
      'Merma',
      'Producto dañado',
      'Consumo interno',
      'Ajuste',
      'Baja por vencimiento',
    ]);
    expect(texts('movement-qty')).toEqual(['+10', '−2', '+1', '−1', '−1', '−1', '−1', '−4']);
    const waste = root.querySelectorAll('[data-testid="detail-movement"]')[3].textContent ?? '';
    expect(waste).toContain('27/11/2026 10:30');
    expect(waste).toContain('Se cayó una jarra');
    expect(waste).toContain('Admin');
  });

  it('CA4 / reload: a write-off with the detail open refreshes its lots, movements and available', async () => {
    await openSangria();
    expect(root.querySelector('[data-testid="detail-available"]')?.textContent?.trim()).toBe('6');

    await askWriteOff();
    button('confirm-accept').click();
    await settle();
    http.expectOne(WRITE_OFF_URL).flush({ lots: 1, units: 4 });
    await settle();
    await fixture.whenStable();
    await flushLists(
      ROWS.map((r) => (r.productId === 3 ? { ...r, expiredUnits: 0, hasExpired: false } : r)),
      { ...SUMMARY, expiredUnits: 0 },
    );

    const writtenOff: Lot = { ...LOTS[1], quantityAvailable: 0, status: 'CONSUMED' };
    http.expectOne(productLotsUrl(3)).flush([LOTS[0], writtenOff, LOTS[2]]);
    http
      .expectOne((r) => r.url === MOVEMENTS_URL && r.params.get('productId') === '3')
      .flush(pageOf([move(9, 'EXPIRED_WRITE_OFF', -4), ...MOVES]));
    await settle();
    await fixture.whenStable();

    expect(root.querySelector('[data-testid="lot-detail"]')).not.toBeNull();
    expect(texts('lot-status')).toEqual(['Consumido', 'Consumido', 'Vigente']);
    expect(texts('movement-type')[0]).toBe('Baja por vencimiento');
    expect(texts('movement-qty')[0]).toBe('−4');
    expect(root.querySelector('[data-testid="detail-available"]')?.textContent?.trim()).toBe('6');
  });

  it('CA4 / reload: the detail available follows the refreshed lots', async () => {
    await openSangria();
    fixture.componentInstance.reload();
    await flushLists();
    http.expectOne(productLotsUrl(3)).flush([{ ...LOTS[0], quantityAvailable: 2 }]);
    http.expectOne((r) => r.url === MOVEMENTS_URL).flush(pageOf(MOVES));
    await settle();
    await fixture.whenStable();
    expect(root.querySelector('[data-testid="detail-available"]')?.textContent?.trim()).toBe('2');
  });

  it('CA4 the close button and Escape close the detail; empty lists say so', async () => {
    await openSangria();
    root.querySelector<HTMLButtonElement>('[data-testid="lot-detail"] .close')!.click();
    await fixture.whenStable();
    expect(root.querySelector('[data-testid="lot-detail"]')).toBeNull();

    await openSangria([], []);
    const detail = root.querySelector('[data-testid="lot-detail"]')!;
    expect(detail.textContent).toContain('Sin lotes registrados.');
    expect(detail.textContent).toContain('Aún no hay movimientos de este producto.');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await fixture.whenStable();
    expect(root.querySelector('[data-testid="lot-detail"]')).toBeNull();
  });

  // ------------------------------------------------------------------ HU-04 / HU-05 + recarga de HU-06

  it('HU-04 CA1 / HU-06 reload: "Registrar entrada" saves, shows the toast, closes and reloads the lists', async () => {
    const before = fixture.componentInstance.reloadTick();
    button('register-lot').click();
    await fixture.whenStable();
    expect(root.querySelector('[role="dialog"]')).not.toBeNull();

    http
      .expectOne((r) => r.url === PRODUCTS_URL)
      .flush({
        content: [{ id: 9, name: 'Vino caliente', seasonName: 'Navidad', active: true }],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
      });
    await settle();
    await fixture.whenStable();

    const select = root.querySelector<HTMLSelectElement>('#li-product')!;
    select.value = '9';
    select.dispatchEvent(new Event('change'));
    for (const [id, value] of [
      ['li-qty', '12'],
      ['li-exp', businessToday()],
    ]) {
      const input = root.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    await fixture.whenStable();
    button('save-lot').click();
    await settle();

    http.expectOne(LOTS_URL).flush(CREATED, { status: 201, statusText: 'Created' });
    await settle();
    await fixture.whenStable();
    await flushLists(ROWS, { ...SUMMARY, expiring: 4 });

    expect(toastsOf()).toEqual([['success', 'Lote registrado: 12 × Vino caliente']]);
    expect(root.querySelector('[role="dialog"]')).toBeNull();
    expect(fixture.componentInstance.reloadTick()).toBe(before + 1);
    expect(
      root.querySelector('[data-testid="stat-expiring"] [data-testid="stat-value"]')?.textContent?.trim(),
    ).toBe('4');
  });

  it('Cancelar closes the form without sending anything', async () => {
    button('register-lot').click();
    await fixture.whenStable();
    http.expectOne((r) => r.url === PRODUCTS_URL).flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    await settle();
    await fixture.whenStable();

    Array.from(root.querySelectorAll<HTMLButtonElement>('.modal-actions .btn'))
      .find((b) => b.textContent?.includes('Cancelar'))!
      .click();
    await fixture.whenStable();

    expect(root.querySelector('[role="dialog"]')).toBeNull();
  });

  async function registerOutput(result: OutputResult): Promise<void> {
    button('register-output').click();
    await fixture.whenStable();
    expect(root.querySelector('[role="dialog"]')).not.toBeNull();
    http.expectOne((r) => r.url === PRODUCTS_URL).flush({
      content: [{ id: 3, name: 'Copa de Malbec', seasonName: null, available: 12, active: true }],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    await settle();
    await fixture.whenStable();

    const select = root.querySelector<HTMLSelectElement>('#lo-product')!;
    select.value = '3';
    select.dispatchEvent(new Event('input'));
    select.dispatchEvent(new Event('change'));
    for (const [id, value] of [
      ['lo-qty', String(result.quantity)],
      ['lo-note', 'Merma de la noche'],
    ]) {
      const input = root.querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    await fixture.whenStable();
    button('save-output').click();
    await settle();

    http.expectOne(OUTPUTS_URL).flush(result);
    await settle();
    await fixture.whenStable();
    // Tras la salida se recargan tabla y contadores.
    await flushLists();
  }

  const OUTPUT: OutputResult = {
    productId: 3,
    productName: 'Copa de Malbec',
    type: 'WASTE_OUT',
    quantity: 5,
    consumed: [
      { lotId: 7, expiresOn: '2026-12-01', quantity: 3 },
      { lotId: 8, expiresOn: '2026-12-20', quantity: 2 },
    ],
    available: 7,
  };

  it('HU-05 CA1 / HU-06 reload: a FEFO output split across lots says so in the toast, closes and reloads', async () => {
    const before = fixture.componentInstance.reloadTick();
    await registerOutput(OUTPUT);

    expect(toastsOf()).toEqual([['success', 'Salida registrada: 5 × Copa de Malbec (de 2 lotes)']]);
    expect(root.querySelector('[role="dialog"]')).toBeNull();
    expect(fixture.componentInstance.reloadTick()).toBe(before + 1);
  });

  it('HU-05 CA2 an output from a single lot shows "Salida registrada: N × Producto"', async () => {
    await registerOutput({ ...OUTPUT, quantity: 2, consumed: [{ lotId: 7, expiresOn: '2026-12-01', quantity: 2 }] });

    expect(toastsOf()).toEqual([['success', 'Salida registrada: 2 × Copa de Malbec']]);
  });

  async function askWriteOff(): Promise<void> {
    button('write-off-expired').click();
    await fixture.whenStable();
  }

  it('HU-05 CA3 "Dar de baja vencidos" asks with the app dialog; Cancelar does not call the endpoint', async () => {
    const nativeConfirm = vi.spyOn(window, 'confirm');
    await askWriteOff();

    const dialog = root.querySelector('[role="alertdialog"]');
    expect(dialog?.textContent).toContain(WRITE_OFF_CONFIRM);
    expect(button('confirm-cancel').textContent?.trim()).toBe('Cancelar');
    expect(button('confirm-accept').textContent?.trim()).toBe('Dar de baja');

    button('confirm-cancel').click();
    await fixture.whenStable();

    http.expectNone(WRITE_OFF_URL);
    expect(root.querySelector('[role="alertdialog"]')).toBeNull();
    expect(nativeConfirm).not.toHaveBeenCalled();
    expect(toastsOf()).toEqual([]);
  });

  it('HU-05 CA3 / HU-06 reload: confirming writes off the expired lots, shows lots and units and reloads', async () => {
    const before = fixture.componentInstance.reloadTick();
    await askWriteOff();
    button('confirm-accept').click();
    await settle();

    const request = http.expectOne(WRITE_OFF_URL);
    expect(request.request.method).toBe('POST');
    request.flush({ lots: 2, units: 9 });
    await settle();
    await fixture.whenStable();
    await flushLists(ROWS, { ...SUMMARY, expiredUnits: 0 });

    expect(toastsOf()).toEqual([['success', 'Se dieron de baja 2 lote(s), 9 unidad(es).']]);
    expect(root.querySelector('[role="alertdialog"]')).toBeNull();
    expect(fixture.componentInstance.reloadTick()).toBe(before + 1);
    expect(
      root.querySelector('[data-testid="stat-expired"] [data-testid="stat-value"]')?.textContent?.trim(),
    ).toBe('0');
  });

  it('HU-05 CA3 with no expired lots it says "No hay lotes vencidos."', async () => {
    await askWriteOff();
    button('confirm-accept').click();
    await settle();

    http.expectOne(WRITE_OFF_URL).flush({ lots: 0, units: 0 });
    await settle();
    await fixture.whenStable();

    expect(toastsOf()).toEqual([['info', NO_EXPIRED_LOTS]]);
  });

  it('HU-05 CA3 a failed write-off is shown as an error toast', async () => {
    await askWriteOff();
    button('confirm-accept').click();
    await settle();

    http
      .expectOne(WRITE_OFF_URL)
      .flush({ status: 403, detail: 'No tienes permiso.' }, { status: 403, statusText: 'Forbidden' });
    await settle();
    await fixture.whenStable();

    expect(toastsOf()).toEqual([['error', 'No tienes permiso.']]);
  });
});
