import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { AppNotification } from '../../core/notifications/notification.model';
import { UNREAD_URL } from '../../core/notifications/notification.service';
import { OrderChangedEvent, SseClient } from '../../core/notifications/sse-client';
import { ToastService } from '../../core/notifications/toast.service';
import { Order, OrderStatus } from '../orders/order.model';
import { ORDERS_URL } from '../orders/orders.service';
import { READY_EMPTY, ReadyOrders } from './ready-orders';

const MINE_URL = `${ORDERS_URL}/mine`;
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

function order(id: number, status: OrderStatus, tableNumber = 3): Order {
  return {
    id,
    tableId: 1,
    tableNumber,
    waiterId: 7,
    waiterName: 'Mateo Ruiz',
    status,
    note: null,
    createdAt: '2026-11-28T20:00:00Z',
    items: [{ id: id * 10, productId: 1, productName: 'Copa de Malbec', unitPrice: 18000, quantity: 2, note: null, subtotal: 36000 }],
    total: 36000,
    readyAt: status === 'READY' ? '2026-11-28T20:10:00Z' : null,
  };
}

describe('ReadyOrders (LCV-HU-14)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ReadyOrders>;
  let root: HTMLElement;
  const lastOrderChange = signal<OrderChangedEvent | null>(null);
  const reconnections = signal(0);
  const connected = signal(false);

  beforeEach(() => {
    lastOrderChange.set(null);
    reconnections.set(0);
    connected.set(false);
    TestBed.configureTestingModule({
      imports: [ReadyOrders],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: SseClient,
          useValue: { lastOrderChange, reconnections, connected, notification$: new Subject<AppNotification>().asObservable() },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function refresh(): Promise<void> {
    await settle();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function render(initial: Order[]): Promise<void> {
    fixture = TestBed.createComponent(ReadyOrders);
    root = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    http.expectOne(MINE_URL).flush(initial);
    await refresh();
  }

  const items = () => Array.from(root.querySelectorAll<HTMLElement>('[data-testid="ready-order"]'));

  it('CA1 lists only my READY orders under "Listos para recoger"', async () => {
    await render([order(1, 'PENDING'), order(2, 'READY', 4), order(3, 'IN_PREPARATION')]);

    expect(root.querySelector('h3')?.textContent).toContain('Listos para recoger');
    expect(root.querySelector('[data-testid="ready-count"]')?.textContent?.trim()).toBe('1');
    expect(items().map((i) => i.querySelector('[data-testid="ready-title-text"]')?.textContent?.trim())).toEqual([
      'Pedido #2 · Mesa 4',
    ]);
    expect(items()[0].querySelector('[data-testid="deliver-order"]')?.textContent?.trim()).toBe('Recogido y entregado');
  });

  it('CA1 shows an empty message when nothing is ready', async () => {
    await render([order(1, 'PENDING')]);
    expect(root.querySelector('[data-testid="ready-empty"]')?.textContent?.trim()).toBe(READY_EMPTY);
  });

  it('CA1 the order appears at once when an order-changed arrives, without reloading the page', async () => {
    await render([order(1, 'IN_PREPARATION')]);
    expect(items()).toHaveLength(0);

    lastOrderChange.set({ orderId: 1, tableId: 1, status: 'READY' });
    TestBed.tick();
    http.expectOne(MINE_URL).flush([order(1, 'READY')]);
    await refresh();

    expect(items()).toHaveLength(1);
  });

  it('CA2 reloads after the stream reconnects', async () => {
    await render([]);
    reconnections.set(1);
    TestBed.tick();
    http.expectOne(MINE_URL).flush([order(9, 'READY')]);
    await refresh();
    expect(items()).toHaveLength(1);
  });

  it('CA3 Recogido y entregado posts deliver once, then refreshes my orders and the unread notifications', async () => {
    await render([order(2, 'READY')]);
    const delivered: Order[] = [];
    fixture.componentInstance.delivered.subscribe((o) => delivered.push(o));

    const button = items()[0].querySelector<HTMLButtonElement>('[data-testid="deliver-order"]')!;
    button.click();
    button.click();
    const request = http.expectOne(`${ORDERS_URL}/2/deliver`);
    expect(request.request.method).toBe('POST');
    request.flush(order(2, 'DELIVERED'));
    await settle();

    http.expectOne(MINE_URL).flush([]);
    http.expectOne(UNREAD_URL).flush([]);
    await refresh();
    expect(items()).toHaveLength(0);
    expect(delivered.map((o) => o.id)).toEqual([2]);
  });

  it('CA3 a 409 on deliver shows the server message and refreshes', async () => {
    await render([order(2, 'READY')]);
    const toast = TestBed.inject(ToastService);

    items()[0].querySelector<HTMLButtonElement>('[data-testid="deliver-order"]')!.click();
    http
      .expectOne(`${ORDERS_URL}/2/deliver`)
      .flush({ status: 409, detail: 'El pedido está entregado y no puede pasar a entregado.' }, { status: 409, statusText: 'Conflict' });
    await settle();
    http.expectOne(MINE_URL).flush([]);
    http.expectOne(UNREAD_URL).flush([]);
    await settle();

    expect(toast.toasts().map((t) => [t.kind, t.message])).toEqual([
      ['error', 'El pedido está entregado y no puede pasar a entregado.'],
    ]);
  });
});
