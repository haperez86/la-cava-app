import { Component, computed, effect, inject, output, signal, untracked } from '@angular/core';
import { problemMessage } from '../../core/http/problem';
import { NotificationService } from '../../core/notifications/notification.service';
import { SseClient, refreshOnReconnect } from '../../core/notifications/sse-client';
import { ToastService } from '../../core/notifications/toast.service';
import { formatBusinessDateTime } from '../../shared/dates/business-date';
import { Order } from '../orders/order.model';
import { OrdersService } from '../orders/orders.service';

export const READY_TITLE = 'Listos para recoger';
export const READY_EMPTY = 'No tienes pedidos listos por recoger.';

/**
 * "Listos para recoger" del mesero (LCV-HU-14, D42): sus pedidos en `READY` (`GET /orders/mine`), con "Recogido y
 * entregado". Se refresca al instante cuando llega un `order-changed` y al reconectar el canal SSE.
 */
@Component({
  selector: 'app-ready-orders',
  templateUrl: './ready-orders.html',
  styleUrl: './ready-orders.scss',
})
export class ReadyOrders {
  readonly #orders = inject(OrdersService);
  readonly #toast = inject(ToastService);
  readonly #notifications = inject(NotificationService);

  /** Un pedido pasó a entregado: la vista padre refresca el estado de sus mesas. */
  readonly delivered = output<Order>();

  protected readonly title = READY_TITLE;
  protected readonly emptyText = READY_EMPTY;
  protected readonly formatTime = formatBusinessDateTime;
  readonly #mine = signal<readonly Order[]>([]);
  protected readonly ready = computed(() => this.#mine().filter((o) => o.status === 'READY'));
  /** Pedidos con la entrega en curso: evitan el doble clic (un segundo `deliver` daría 409). */
  protected readonly busy = signal<ReadonlySet<number>>(new Set());

  constructor() {
    void this.load();
    refreshOnReconnect(() => void this.load());
    // CA1: el pedido aparece al instante, sin recargar, cuando Preparación lo marca listo.
    const sse = inject(SseClient);
    effect(() => {
      if (sse.lastOrderChange()) {
        untracked(() => void this.load());
      }
    });
  }

  protected isBusy(order: Order): boolean {
    return this.busy().has(order.id);
  }

  /** CA3: `READY → DELIVERED` y sus avisos quedan leídos (el contador de la campana baja). */
  protected async deliver(order: Order): Promise<void> {
    if (this.isBusy(order)) {
      return;
    }
    this.busy.update((set) => new Set(set).add(order.id));
    try {
      await this.#orders.deliver(order.id);
      this.delivered.emit(order);
    } catch (e) {
      this.#toast.error(problemMessage(e));
    } finally {
      this.busy.update((set) => {
        const next = new Set(set);
        next.delete(order.id);
        return next;
      });
    }
    await Promise.all([this.load(), this.#notifications.load()]);
  }

  private async load(): Promise<void> {
    try {
      this.#mine.set(await this.#orders.mine());
    } catch (e) {
      this.#toast.error(problemMessage(e));
    }
  }
}
