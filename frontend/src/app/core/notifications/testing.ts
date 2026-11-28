import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { AppNotification } from './notification.model';
import { OrderChangedEvent, SseClient } from './sse-client';

/** `SseClient` falso para pruebas de vistas: se emiten `order-changed` y `notification` a mano. */
export function fakeSseClient() {
  const lastOrderChange = signal<OrderChangedEvent | null>(null);
  const notifications = new Subject<AppNotification>();
  const stub = {
    start: () => undefined,
    stop: () => undefined,
    connected: signal(false),
    reconnections: signal(0),
    lastOrderChange: lastOrderChange.asReadonly(),
    notification$: notifications.asObservable(),
  };
  return {
    provider: { provide: SseClient, useValue: stub },
    orderChanged: (orderId = 1) => lastOrderChange.set({ orderId, tableId: 1, status: 'PENDING' }),
    notify: (n: AppNotification) => notifications.next(n),
  };
}
