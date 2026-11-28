import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { storeTestSession } from '../auth/testing';
import { KitchenPage } from '../../features/kitchen/kitchen-page';
import { KITCHEN_BOARD_URL } from '../../features/kitchen/kitchen.service';
import { ORDERS_URL } from '../../features/orders/orders.service';
import { OrdersPage } from '../../features/orders/orders-page';
import { TablesBoard } from '../../features/tables/tables-board';
import { TABLES_URL } from '../../features/tables/tables.service';
import { WaiterTables } from '../../features/tables/waiter-tables';
import { AppNotification } from './notification.model';
import { readNotificationUrl, UNREAD_URL } from './notification.service';
import { REFRESH_BURST_MS } from './sse-client';
import { fakeSseClient } from './testing';

const created = (id: number): AppNotification => ({
  id,
  type: 'ORDER_CREATED',
  orderId: id,
  tableNumber: 3,
  message: `Nuevo pedido #${id} · Mesa 3`,
  createdAt: '2026-11-28T20:00:00Z',
  readAt: null,
});

describe('Refresco automático ante order-changed (LCV-HU-15, D43)', () => {
  let http: HttpTestingController;
  let sse: ReturnType<typeof fakeSseClient>;

  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    sse = fakeSseClient();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), sse.provider] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  const count = (matcher: (url: string) => boolean) => http.match((r) => matcher(r.url)).length;
  const clear = () => http.match(() => true);

  function open<T>(component: new () => T): void {
    TestBed.createComponent(component);
    TestBed.tick();
    clear();
  }

  it.each([
    ['CA2 the cashier tables board', 'CASHIER', TablesBoard, (u: string) => u === TABLES_URL],
    ['CA2 the waiter tables', 'WAITER', WaiterTables, (u: string) => u === TABLES_URL],
    ['CA2 the orders list', 'ADMIN', OrdersPage, (u: string) => u.startsWith(ORDERS_URL)],
    ['CA2 the kitchen board', 'KITCHEN', KitchenPage, (u: string) => u === KITCHEN_BOARD_URL],
  ] as const)('%s reloads on order-changed without reloading the page', async (_name, role, component, matcher) => {
    storeTestSession(role);
    open(component as new () => unknown);

    sse.orderChanged();
    TestBed.tick();
    expect(count(matcher)).toBe(0);
    await vi.advanceTimersByTimeAsync(REFRESH_BURST_MS);
    expect(count(matcher)).toBeGreaterThan(0);
  });

  it('CA2 a burst of order-changed events produces a single reload', async () => {
    storeTestSession('CASHIER');
    open(TablesBoard);

    for (let i = 1; i <= 5; i++) {
      sse.orderChanged(i);
      TestBed.tick();
      await vi.advanceTimersByTimeAsync(50);
    }
    await vi.advanceTimersByTimeAsync(REFRESH_BURST_MS);
    expect(count((u) => u === TABLES_URL)).toBe(1);
    await vi.advanceTimersByTimeAsync(REFRESH_BURST_MS * 2);
    expect(count((u) => u === TABLES_URL)).toBe(0);
  });

  it('CA2 a view that was closed does not reload', async () => {
    storeTestSession('CASHIER');
    const fixture = TestBed.createComponent(TablesBoard);
    TestBed.tick();
    clear();

    sse.orderChanged();
    TestBed.tick();
    fixture.destroy();
    await vi.advanceTimersByTimeAsync(REFRESH_BURST_MS * 2);
    expect(count((u) => u === TABLES_URL)).toBe(0);
  });

  it('CA1 the kitchen board reloads on a notification ORDER_CREATED and a burst with order-changed reloads once', async () => {
    storeTestSession('KITCHEN');
    open(KitchenPage);

    sse.notify(created(1));
    sse.orderChanged();
    TestBed.tick();
    await vi.advanceTimersByTimeAsync(REFRESH_BURST_MS);

    expect(count((u) => u === KITCHEN_BOARD_URL)).toBe(1);
  });

  it('CA1 opening the board marks the ORDER_CREATED notifications as read, also those arriving later', async () => {
    storeTestSession('KITCHEN');
    open(KitchenPage);

    sse.notify(created(7));
    TestBed.tick();
    const first = http.expectOne(readNotificationUrl(7));
    expect(first.request.method).toBe('POST');
    first.flush(null);
    await vi.advanceTimersByTimeAsync(0);
    http.expectNone(UNREAD_URL);
  });
});
