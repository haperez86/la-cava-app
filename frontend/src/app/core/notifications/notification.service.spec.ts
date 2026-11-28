import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { AppNotification } from './notification.model';
import { NotificationService, UNREAD_URL, VIBRATION_PATTERN_MS } from './notification.service';
import { SseClient } from './sse-client';
import { ToastService } from './toast.service';

const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

function notice(id: number, orderId = 5, table = 3): AppNotification {
  return {
    id,
    type: 'ORDER_READY',
    orderId,
    tableNumber: table,
    message: `Pedido #${orderId} listo · Mesa ${table}`,
    createdAt: '2026-11-28T20:00:00Z',
    readAt: null,
  };
}

describe('NotificationService (LCV-HU-14)', () => {
  let http: HttpTestingController;
  let service: NotificationService;
  let toast: ToastService;
  let vibrate: ReturnType<typeof vi.fn>;
  const connected = signal(false);
  let stream: Subject<AppNotification>;

  beforeEach(() => {
    connected.set(false);
    stream = new Subject<AppNotification>();
    vibrate = vi.fn(() => true);
    Object.defineProperty(navigator, 'vibrate', { value: vibrate, configurable: true });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SseClient, useValue: { connected, notification$: stream.asObservable() } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    toast = TestBed.inject(ToastService);
    service = TestBed.inject(NotificationService);
  });

  afterEach(() => {
    http.verify();
    delete (navigator as { vibrate?: unknown }).vibrate;
  });

  it('CA1 an incoming notification shows the "ready" toast, vibrates and raises the bell counter', () => {
    expect(service.count()).toBe(0);

    stream.next(notice(1, 7, 4));

    expect(toast.toasts().map((t) => [t.kind, t.message])).toEqual([['ready', 'Pedido #7 listo · Mesa 4']]);
    expect(vibrate).toHaveBeenCalledWith([...VIBRATION_PATTERN_MS]);
    expect(service.count()).toBe(1);

    stream.next(notice(2, 8, 2));
    stream.next(notice(2, 8, 2)); // el mismo aviso no cuenta dos veces
    expect(service.count()).toBe(2);
  });

  it('CA1 works without navigator.vibrate (desktop browsers)', () => {
    delete (navigator as { vibrate?: unknown }).vibrate;
    stream.next(notice(1));
    expect(service.count()).toBe(1);
  });

  it('CA2 loads the unread notifications when the stream connects and again after a reconnection', async () => {
    connected.set(true);
    TestBed.tick();
    http.expectOne(UNREAD_URL).flush([notice(1), notice(2, 6)]);
    await settle();
    expect(service.count()).toBe(2);
    expect(service.unread().map((n) => n.id)).toEqual([1, 2]);

    connected.set(false);
    TestBed.tick();
    http.expectNone(UNREAD_URL);
    connected.set(true);
    TestBed.tick();
    http.expectOne(UNREAD_URL).flush([notice(2, 6)]);
    await settle();
    expect(service.count()).toBe(1);
  });

  it('CA3 load() after delivering drops the notifications the backend marked as read', async () => {
    stream.next(notice(1));
    const done = service.load();
    http.expectOne(UNREAD_URL).flush([]);
    await done;
    expect(service.count()).toBe(0);
  });

  it('CA2 a failed unread request keeps the current list', async () => {
    stream.next(notice(1));
    const done = service.load();
    http.expectOne(UNREAD_URL).flush(null, { status: 500, statusText: 'Error' });
    await done;
    expect(service.count()).toBe(1);
  });
});
