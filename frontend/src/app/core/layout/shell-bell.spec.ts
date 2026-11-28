import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject } from 'rxjs';
import { Role } from '../auth/role';
import { storeTestSession } from '../auth/testing';
import { AppNotification } from '../notifications/notification.model';
import { SseClient } from '../notifications/sse-client';
import { Shell } from './shell';

@Component({ template: '' })
class Blank {}

describe('Shell bell (LCV-HU-14)', () => {
  let http: HttpTestingController;
  let stream: Subject<AppNotification>;

  beforeEach(() => {
    localStorage.clear();
    stream = new Subject<AppNotification>();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '', component: Shell, children: [{ path: 'mesas', component: Blank }] }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: SseClient,
          useValue: {
            start: () => undefined,
            stop: () => undefined,
            connected: signal(false),
            reconnections: signal(0),
            lastOrderChange: signal(null),
            notification$: stream.asObservable(),
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    Object.defineProperty(navigator, 'vibrate', { value: vi.fn(), configurable: true });
  });

  afterEach(() => {
    http.match('/api/v1/health');
    http.verify();
    delete (navigator as { vibrate?: unknown }).vibrate;
    localStorage.clear();
  });

  let harness: RouterTestingHarness;

  async function open(role: Role): Promise<HTMLElement> {
    storeTestSession(role);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/mesas');
    harness.detectChanges();
    return harness.fixture.nativeElement as HTMLElement;
  }

  const notice = (id: number): AppNotification => ({
    id,
    type: 'ORDER_READY',
    orderId: id,
    tableNumber: 3,
    message: `Pedido #${id} listo · Mesa 3`,
    createdAt: '2026-11-28T20:00:00Z',
    readAt: null,
  });

  it('CA1 the waiter bell shows the unread counter that grows with each notification', async () => {
    const root = await open('WAITER');
    const bell = root.querySelector<HTMLAnchorElement>('[data-testid="bell"]')!;
    expect(bell.getAttribute('aria-label')).toBe('Avisos: ninguno sin leer');
    expect(root.querySelector('[data-testid="bell-count"]')).toBeNull();

    stream.next(notice(1));
    stream.next(notice(2));
    harness.detectChanges();

    expect(root.querySelector('[data-testid="bell-count"]')?.textContent?.trim()).toBe('2');
    expect(bell.getAttribute('aria-label')).toBe('Avisos: 2 sin leer');
  });

  it.each<Role>(['ADMIN', 'KITCHEN'])('CA1 %s does not get the waiter bell', async (role) => {
    const root = await open(role);
    expect(root.querySelector('[data-testid="bell"]')).toBeNull();
  });
});
