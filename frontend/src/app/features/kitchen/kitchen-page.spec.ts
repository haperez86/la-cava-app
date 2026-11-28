import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/notifications/toast.service';
import { HtmlPrinter, PRINT_ERROR } from '../../core/print/print-html';
import { Order, OrderStatus } from '../orders/order.model';
import { comandaHtml } from './comanda-ticket';
import { KITCHEN_BOARD_URL, KitchenService, readyOrderUrl, startOrderUrl } from './kitchen.service';
import { KITCHEN_EMPTY, KitchenPage } from './kitchen-page';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

function order(id: number, status: OrderStatus, overrides: Partial<Order> = {}): Order {
  return {
    id,
    tableId: 1,
    tableNumber: 3,
    waiterId: 7,
    waiterName: 'Laura Gómez',
    status,
    note: null,
    createdAt: '2026-11-27T20:00:00Z',
    items: [{ id: id * 10, productId: 1, productName: 'Copa de Malbec', unitPrice: 18000, quantity: 2, note: null, subtotal: 36000 }],
    total: 36000,
    ...overrides,
  };
}

const BOARD: Order[] = [
  order(5, 'PENDING', {
    note: 'Sin hielo',
    items: [{ id: 50, productId: 1, productName: 'Tabla de quesos', unitPrice: 42000, quantity: 1, note: 'Sin nueces', subtotal: 42000 }],
  }),
  order(4, 'IN_PREPARATION', { waiterName: 'Andrés Rojas', startedAt: '2026-11-27T20:05:00Z' }),
  order(3, 'READY', { startedAt: '2026-11-27T20:05:00Z', readyAt: '2026-11-27T20:10:00Z' }),
];

describe('KitchenService (LCV-HU-12)', () => {
  let http: HttpTestingController;
  let service: KitchenService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(KitchenService);
  });

  afterEach(() => http.verify());

  it('CA1 board() reads GET /kitchen/board', async () => {
    const result = service.board();
    const request = http.expectOne(KITCHEN_BOARD_URL);
    expect(request.request.method).toBe('GET');
    request.flush(BOARD);
    expect(await result).toEqual(BOARD);
  });

  it('CA2 start() and ready() post to /orders/{id}/start and /ready', async () => {
    const started = service.start(5);
    const start = http.expectOne(startOrderUrl(5));
    expect(start.request.method).toBe('POST');
    start.flush(order(5, 'IN_PREPARATION'));
    expect((await started).status).toBe('IN_PREPARATION');

    const done = service.ready(4);
    const ready = http.expectOne(readyOrderUrl(4));
    expect(ready.request.method).toBe('POST');
    ready.flush(order(4, 'READY'));
    expect((await done).status).toBe('READY');
  });
});

describe('KitchenPage (LCV-HU-12)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<KitchenPage>;
  let root: HTMLElement;
  let print: ReturnType<typeof vi.fn>;

  async function flushBoard(rows: readonly Order[] = BOARD): Promise<void> {
    http.expectOne(KITCHEN_BOARD_URL).flush(rows);
    await settle();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    print = vi.fn().mockResolvedValue(undefined);
    TestBed.configureTestingModule({
      imports: [KitchenPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: HtmlPrinter, useValue: { print } }],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(KitchenPage);
    root = fixture.nativeElement as HTMLElement;
    await flushBoard();
  });

  afterEach(() => http.verify());

  const column = (status: OrderStatus) => root.querySelector<HTMLElement>(`[data-testid="col-${status}"]`)!;
  const inColumn = (status: OrderStatus, testId: string) =>
    Array.from(column(status).querySelectorAll<HTMLElement>(`[data-testid="${testId}"]`));
  const toastsOf = () => TestBed.inject(ToastService).toasts().map((t) => [t.kind, t.message]);

  it('CA1 shows PENDING, IN_PREPARATION and READY in their own column with table, waiter, items, notes and time', () => {
    expect(inColumn('PENDING', 'comanda-number').map((e) => e.textContent?.trim())).toEqual(['Pedido #5']);
    expect(inColumn('IN_PREPARATION', 'comanda-number').map((e) => e.textContent?.trim())).toEqual(['Pedido #4']);
    expect(inColumn('READY', 'comanda-number').map((e) => e.textContent?.trim())).toEqual(['Pedido #3']);
    expect(inColumn('PENDING', 'comanda-table')[0].textContent?.trim()).toBe('Mesa 3');
    expect(inColumn('PENDING', 'comanda-waiter')[0].textContent?.trim()).toBe('Laura Gómez');
    expect(inColumn('PENDING', 'comanda-item')[0].textContent).toContain('1 × Tabla de quesos');
    expect(inColumn('PENDING', 'comanda-item-note')[0].textContent).toContain('Sin nueces');
    expect(inColumn('PENDING', 'comanda-note')[0].textContent).toContain('Sin hielo');
    expect(inColumn('PENDING', 'comanda-time')[0].textContent?.trim()).not.toBe('');
    expect(inColumn('READY', 'comanda-status')[0].textContent?.trim()).toBe('Listo');
    expect(root.querySelector('[data-testid="count-PENDING"]')?.textContent?.trim()).toBe('1');
  });

  it('CA1 shows a neutral empty state when the board has no orders and refreshes on demand', async () => {
    root.querySelector<HTMLButtonElement>('[data-testid="refresh"]')!.click();
    await flushBoard([]);
    expect(root.querySelector('[data-testid="kitchen-empty"]')?.textContent).toContain(KITCHEN_EMPTY);
  });

  it('CA2 buttons depend on the status: Iniciar preparación only in PENDING, Listo · avisar a <mesero> only in IN_PREPARATION', () => {
    expect(inColumn('PENDING', 'start-order')[0].textContent?.trim()).toBe('Iniciar preparación');
    expect(inColumn('PENDING', 'ready-order')).toHaveLength(0);
    expect(inColumn('IN_PREPARATION', 'start-order')).toHaveLength(0);
    expect(inColumn('IN_PREPARATION', 'ready-order')[0].textContent?.trim()).toBe('Listo · avisar a Andrés Rojas');
    expect(inColumn('READY', 'start-order')).toHaveLength(0);
    expect(inColumn('READY', 'ready-order')).toHaveLength(0);
  });

  it('CA2 Iniciar preparación posts start once (double click ignored) and reloads the board', async () => {
    const button = inColumn('PENDING', 'start-order')[0] as HTMLButtonElement;
    button.click();
    button.click();
    http.expectOne(startOrderUrl(5)).flush(order(5, 'IN_PREPARATION'));
    await settle();
    http.expectOne(KITCHEN_BOARD_URL).flush([order(5, 'IN_PREPARATION'), BOARD[1], BOARD[2]]);
    await settle();
    fixture.detectChanges();
    expect(inColumn('PENDING', 'comanda')).toHaveLength(0);
    expect(inColumn('IN_PREPARATION', 'comanda')).toHaveLength(2);
  });

  it('CA2 Listo · avisar a <mesero> posts ready and the order moves to the READY column', async () => {
    (inColumn('IN_PREPARATION', 'ready-order')[0] as HTMLButtonElement).click();
    http.expectOne(readyOrderUrl(4)).flush(order(4, 'READY'));
    await settle();
    http.expectOne(KITCHEN_BOARD_URL).flush([BOARD[0], order(4, 'READY'), BOARD[2]]);
    await settle();
    fixture.detectChanges();
    expect(inColumn('IN_PREPARATION', 'comanda')).toHaveLength(0);
    expect(inColumn('READY', 'comanda')).toHaveLength(2);
  });

  it('CA2 a 409 shows the server message in a toast and refreshes the board', async () => {
    (inColumn('PENDING', 'start-order')[0] as HTMLButtonElement).click();
    http
      .expectOne(startOrderUrl(5))
      .flush({ status: 409, detail: 'El pedido está en preparación y no puede pasar a en preparación.' }, { status: 409, statusText: 'Conflict' });
    await settle();
    http.expectOne(KITCHEN_BOARD_URL).flush([order(5, 'IN_PREPARATION'), BOARD[1], BOARD[2]]);
    await settle();
    expect(toastsOf()[0]).toEqual(['error', 'El pedido está en preparación y no puede pasar a en preparación.']);
  });

  it('CA3 Imprimir comanda sends the 80 mm ticket of that order to the printer', async () => {
    (inColumn('PENDING', 'print-order')[0] as HTMLButtonElement).click();
    await settle();
    expect(print).toHaveBeenCalledTimes(1);
    const html = print.mock.calls[0][0] as string;
    expect(html).toBe(comandaHtml(BOARD[0]));
    expect(html).toContain('COMANDA DE PREPARACIÓN');
  });

  it('CA3 a printing failure shows an error toast', async () => {
    print.mockRejectedValueOnce(new Error(PRINT_ERROR));
    (inColumn('READY', 'print-order')[0] as HTMLButtonElement).click();
    await settle();
    expect(toastsOf()).toContainEqual(['error', PRINT_ERROR]);
  });
});

describe('comandaHtml (LCV-HU-12)', () => {
  it('CA3 ticket is 72 mm wide with table, waiter, items, notes and escapes user text', () => {
    const html = comandaHtml(
      order(9, 'PENDING', {
        note: '<b>sin hielo</b>',
        items: [{ id: 1, productId: 1, productName: 'Gin & Tonic', unitPrice: 1, quantity: 2, note: 'poco "dulce"', subtotal: 2 }],
      }),
    );
    expect(html).toContain('width:72mm');
    expect(html).toContain('size:80mm auto');
    expect(html).toContain('Pedido: #9');
    expect(html).toContain('Mesa: <b>3</b>');
    expect(html).toContain('Mesero: Laura Gómez');
    expect(html).toContain('<b>2</b>');
    expect(html).toContain('Gin &amp; Tonic');
    expect(html).toContain('» poco &quot;dulce&quot;');
    expect(html).toContain('Obs.: &lt;b&gt;sin hielo&lt;/b&gt;');
  });
});
