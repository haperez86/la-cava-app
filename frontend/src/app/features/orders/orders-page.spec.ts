import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/notifications/toast.service';
import { CANCEL_REASON_MAX_LENGTH, CANCEL_REASON_REQUIRED } from './cancel-order-dialog';
import { Order, OrderStatus, OrderSummary } from './order.model';
import { ORDERS_PAGE_SIZE, OrdersPage, SEARCH_DEBOUNCE_MS, orderCancelledMessage } from './orders-page';
import { ORDERS_URL } from './orders.service';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));
const SUMMARY_URL = `${ORDERS_URL}/summary`;

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

const ROWS: Order[] = [
  order(5, 'PENDING'),
  order(4, 'IN_PREPARATION'),
  order(3, 'READY'),
  order(2, 'CANCELLED', { cancelReason: 'Cliente se fue', cancelledAt: '2026-11-27T21:00:00Z' }),
];

const SUMMARY: OrderSummary = { all: 4, PENDING: 1, IN_PREPARATION: 1, READY: 1, DELIVERED: 0, CLOSED: 0, CANCELLED: 1 };

function pageOf(content: readonly Order[], totalElements = content.length) {
  return {
    content,
    page: 0,
    size: ORDERS_PAGE_SIZE,
    totalElements,
    totalPages: Math.max(1, Math.ceil(totalElements / ORDERS_PAGE_SIZE)),
  };
}

describe('OrdersPage (LCV-HU-11)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<OrdersPage>;
  let root: HTMLElement;

  async function flushLists(rows: readonly Order[] = ROWS, summary: OrderSummary = SUMMARY): Promise<void> {
    http.expectOne((r) => r.url === ORDERS_URL).flush(pageOf(rows));
    http.expectOne(SUMMARY_URL).flush(summary);
    await settle();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [OrdersPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrdersPage);
    root = fixture.nativeElement as HTMLElement;
    await flushLists();
  });

  afterEach(() => http.verify());

  const text = (testId: string) =>
    Array.from(root.querySelectorAll<HTMLElement>(`[data-testid="${testId}"]`)).map((e) => e.textContent?.trim());
  const button = (testId: string) => root.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)!;
  const toastsOf = () => TestBed.inject(ToastService).toasts().map((t) => [t.kind, t.message]);

  async function openCancelFor(index: number): Promise<void> {
    root.querySelectorAll<HTMLButtonElement>('[data-testid="cancel-order"]')[index].click();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function typeReason(value: string): void {
    const area = root.querySelector<HTMLTextAreaElement>('#cancel-reason')!;
    area.value = value;
    area.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('CA1 shows the orders newest first with table, waiter, items and status pill', () => {
    expect(text('row-number')).toEqual(['#5', '#4', '#3', '#2']);
    expect(text('row-table')[0]).toBe('Mesa 3');
    expect(text('row-waiter')[0]).toBe('Laura Gómez');
    expect(text('row-status')).toEqual(['Pendiente', 'En preparación', 'Listo', 'Cancelado']);
    expect(text('row-reason')).toEqual(['Cliente se fue']);
  });

  it('CA1 the status pills show the counters from /orders/summary', () => {
    expect(text('count-all')).toEqual(['4']);
    expect(text('count-PENDING')).toEqual(['1']);
    expect(text('count-DELIVERED')).toEqual(['0']);
    expect(text('count-CANCELLED')).toEqual(['1']);
  });

  it('CA1 a status pill filters the list by that status and goes back to page 0', async () => {
    button('status-PENDING').click();
    const request = http.expectOne((r) => r.url === ORDERS_URL);
    expect(request.request.params.get('status')).toBe('PENDING');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(pageOf([ROWS[0]]));
    await settle();
    fixture.detectChanges();

    expect(button('status-PENDING').getAttribute('aria-pressed')).toBe('true');
    expect(text('row-number')).toEqual(['#5']);
    // Los contadores ignoran el estado: no se vuelven a pedir.
    http.expectNone(SUMMARY_URL);

    button('status-all').click();
    const all = http.expectOne((r) => r.url === ORDERS_URL);
    expect(all.request.params.has('status')).toBe(false);
    all.flush(pageOf(ROWS));
  });

  it('CA1 the search waits 250 ms, then sends q to the list and to the counters', async () => {
    vi.useFakeTimers();
    try {
      const input = root.querySelector<HTMLInputElement>('#orders-search')!;
      for (const value of ['la', 'laura']) {
        input.value = value;
        input.dispatchEvent(new Event('input'));
        await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS - 1);
      }
      http.expectNone((r) => r.url === ORDERS_URL);
      await vi.advanceTimersByTimeAsync(1);
      const list = http.expectOne((r) => r.url === ORDERS_URL);
      expect(list.request.params.get('q')).toBe('laura');
      list.flush(pageOf([ROWS[0]]));
      const summary = http.expectOne((r) => r.url === SUMMARY_URL);
      expect(summary.request.params.get('q')).toBe('laura');
      expect(summary.request.params.has('status')).toBe(false);
      summary.flush(SUMMARY);
    } finally {
      vi.useRealTimers();
    }
  });

  it('CA2 only pending orders offer "Cancelar"', () => {
    const rowEls = Array.from(root.querySelectorAll<HTMLElement>('[data-testid="order-row"]'));
    const withCancel = rowEls.filter((el) => el.querySelector('[data-testid="cancel-order"]')).map((el) => el.querySelector('[data-testid="row-number"]')!.textContent!.trim());
    expect(withCancel).toEqual(['#5']);
  });

  it('CA2 the reason is required: an empty reason shows the error and sends nothing', async () => {
    await openCancelFor(0);
    expect(root.querySelector('[role="dialog"]')).not.toBeNull();

    button('cancel-confirm').click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text('error-reason')).toEqual([CANCEL_REASON_REQUIRED]);
    http.expectNone(`${ORDERS_URL}/5/cancel`);
  });

  it('CA2 the reason is limited to 120 characters', async () => {
    await openCancelFor(0);
    typeReason('x'.repeat(CANCEL_REASON_MAX_LENGTH + 1));
    button('cancel-confirm').click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text('error-reason')).toEqual([`Máximo ${CANCEL_REASON_MAX_LENGTH} caracteres.`]);
    http.expectNone(`${ORDERS_URL}/5/cancel`);
  });

  it('CA2 cancelling sends the trimmed reason, shows a toast and refreshes the list and counters', async () => {
    await openCancelFor(0);
    typeReason('  Cliente se fue  ');
    button('cancel-confirm').click();
    const request = http.expectOne(`${ORDERS_URL}/5/cancel`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ reason: 'Cliente se fue' });
    const cancelled = order(5, 'CANCELLED', { cancelReason: 'Cliente se fue' });
    request.flush(cancelled);
    await settle();
    await flushLists([order(4, 'IN_PREPARATION'), cancelled], { ...SUMMARY, PENDING: 0, CANCELLED: 2 });
    fixture.detectChanges();

    expect(toastsOf()).toEqual([['success', orderCancelledMessage(cancelled)]]);
    expect(root.querySelector('[role="dialog"]')).toBeNull();
    expect(text('count-PENDING')).toEqual(['0']);
    expect(text('count-CANCELLED')).toEqual(['2']);
  });

  it('CA3 a 409 (the order is no longer pending) shows the server message and refreshes the list', async () => {
    await openCancelFor(0);
    typeReason('Tarde');
    button('cancel-confirm').click();
    http
      .expectOne(`${ORDERS_URL}/5/cancel`)
      .flush(
        { status: 409, detail: 'Solo se puede cancelar un pedido pendiente.' },
        { status: 409, statusText: 'Conflict' },
      );
    await settle();
    await flushLists([order(5, 'IN_PREPARATION')], { ...SUMMARY, PENDING: 0 });
    fixture.detectChanges();

    expect(toastsOf()).toEqual([['error', 'Solo se puede cancelar un pedido pendiente.']]);
    expect(root.querySelector('[role="dialog"]')).toBeNull();
    expect(root.querySelector('[data-testid="cancel-order"]')).toBeNull();
  });
});
