import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  BACKOFF_MAX_MS,
  EVENT_SOURCE_FACTORY,
  SseClient,
  STREAM_TICKET_URL,
  STREAM_URL,
  backoffDelay,
  refreshOnReconnect,
} from './sse-client';

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;
  readonly listeners = new Map<string, (e: MessageEvent<string>) => void>();

  constructor(readonly url: string) {
    FakeEventSource.instances.push(this);
  }

  addEventListener(name: string, listener: (e: MessageEvent<string>) => void): void {
    this.listeners.set(name, listener);
  }

  close(): void {
    this.closed = true;
  }

  emit(name: string, data: string): void {
    this.listeners.get(name)?.({ data } as MessageEvent<string>);
  }
}

describe('SseClient', () => {
  let http: HttpTestingController;
  let sse: SseClient;

  beforeEach(() => {
    vi.useFakeTimers();
    FakeEventSource.instances = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: EVENT_SOURCE_FACTORY, useValue: (url: string) => new FakeEventSource(url) },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    sse = TestBed.inject(SseClient);
  });

  afterEach(() => {
    sse.stop();
    vi.useRealTimers();
  });

  async function grantTicket(ticket: string): Promise<FakeEventSource> {
    http.expectOne(STREAM_TICKET_URL).flush({ ticket });
    await vi.advanceTimersByTimeAsync(0);
    return FakeEventSource.instances.at(-1)!;
  }

  it('CA1 pide un ticket y abre el stream con él; el ticket se envía codificado', async () => {
    sse.start();
    const source = await grantTicket('a+b/c');
    expect(source.url).toBe(`${STREAM_URL}?ticket=a%2Bb%2Fc`);
    source.onopen?.();
    expect(sse.connected()).toBe(true);
    expect(sse.reconnections()).toBe(0);
  });

  it('expone el último order-changed recibido y descarta datos mal formados', async () => {
    sse.start();
    const source = await grantTicket('t1');
    source.emit('order-changed', '{"orderId":5,"tableId":2,"status":"READY"}');
    expect(sse.lastOrderChange()).toEqual({ orderId: 5, tableId: 2, status: 'READY' });
    source.emit('order-changed', 'no-es-json');
    expect(sse.lastOrderChange()?.orderId).toBe(5);
  });

  it('LCV-HU-14 CA1 emite cada evento notification recibido y descarta datos mal formados', async () => {
    const received: number[] = [];
    sse.notification$.subscribe((n) => received.push(n.id));
    sse.start();
    const source = await grantTicket('t1');
    const payload = (id: number) =>
      `{"id":${id},"type":"ORDER_READY","orderId":5,"tableNumber":3,"message":"Pedido #5 listo · Mesa 3","createdAt":"2026-11-28T20:00:00Z","readAt":null}`;
    source.emit('notification', payload(1));
    source.emit('notification', payload(2));
    source.emit('notification', 'no-es-json');
    expect(received).toEqual([1, 2]);
  });

  it('CA2 backoff exponencial de 1 s a 30 s', () => {
    expect([0, 1, 2, 3, 4, 5, 6, 10].map(backoffDelay)).toEqual([1000, 2000, 4000, 8000, 16000, 30000, 30000, 30000]);
    expect(backoffDelay(50)).toBe(BACKOFF_MAX_MS);
  });

  it('CA2 si se cae la conexión reconecta con backoff y un ticket nuevo, y avisa la reconexión', async () => {
    sse.start();
    const first = await grantTicket('t1');
    first.onopen?.();

    first.onerror?.();
    expect(first.closed).toBe(true);
    expect(sse.connected()).toBe(false);

    await vi.advanceTimersByTimeAsync(999);
    http.expectNone(STREAM_TICKET_URL);
    await vi.advanceTimersByTimeAsync(1);
    const second = await grantTicket('t2');
    expect(second.url).toContain('ticket=t2');

    // Falla otra vez antes de abrir: la espera sube a 2 s.
    second.onerror?.();
    await vi.advanceTimersByTimeAsync(1999);
    http.expectNone(STREAM_TICKET_URL);
    await vi.advanceTimersByTimeAsync(1);
    const third = await grantTicket('t3');

    third.onopen?.();
    expect(sse.connected()).toBe(true);
    expect(sse.reconnections()).toBe(1);

    // Tras abrir, el backoff vuelve a 1 s.
    third.onerror?.();
    await vi.advanceTimersByTimeAsync(1000);
    await grantTicket('t4');
  });

  it('CA2 si falla la petición del ticket también reintenta con backoff', async () => {
    sse.start();
    http.expectOne(STREAM_TICKET_URL).flush(null, { status: 500, statusText: 'Error' });
    await vi.advanceTimersByTimeAsync(1000);
    const source = await grantTicket('t2');
    expect(source.url).toContain('ticket=t2');
  });

  it('stop cierra el stream y cancela los reintentos', async () => {
    sse.start();
    const source = await grantTicket('t1');
    source.onopen?.();
    source.onerror?.();
    sse.stop();
    await vi.advanceTimersByTimeAsync(60_000);
    http.expectNone(STREAM_TICKET_URL);
    expect(sse.connected()).toBe(false);
  });

  it('start es idempotente', async () => {
    sse.start();
    sse.start();
    http.expectOne(STREAM_TICKET_URL).flush({ ticket: 't1' });
    await vi.advanceTimersByTimeAsync(0);
    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it('CA2 refreshOnReconnect refresca al reconectar y no en la primera conexión', async () => {
    const refresh = vi.fn();

    @Component({ selector: 'app-probe', template: '' })
    class Probe {
      constructor() {
        refreshOnReconnect(refresh);
      }
    }

    const fixture = TestBed.createComponent(Probe);
    fixture.detectChanges();

    sse.start();
    const first = await grantTicket('t1');
    first.onopen?.();
    fixture.detectChanges();
    expect(refresh).not.toHaveBeenCalled();

    first.onerror?.();
    await vi.advanceTimersByTimeAsync(1000);
    const second = await grantTicket('t2');
    second.onopen?.();
    fixture.detectChanges();
    expect(refresh).toHaveBeenCalledTimes(1);
  });
});
