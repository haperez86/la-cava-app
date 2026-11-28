import { Component, DestroyRef, inject, signal } from '@angular/core';
import { problemMessage } from '../../core/http/problem';
import { ToastService } from '../../core/notifications/toast.service';
import { formatBusinessDateTime } from '../../shared/dates/business-date';
import { DEFAULT_PAGE_SIZE, Pagination } from '../../shared/pagination/pagination';
import { formatCop } from '../../shared/pipes/cop-currency.pipe';
import { CancelOrderDialog } from './cancel-order-dialog';
import { OrderDialog } from './order-dialog';
import {
  ORDER_STATUS_FILTERS,
  ORDER_STATUS_LABEL,
  ORDER_STATUS_PILL,
  Order,
  OrderStatusFilter,
  OrderSummary,
} from './order.model';
import { OrdersService } from './orders.service';

export const ORDERS_EMPTY = 'Los pedidos creados aparecerán aquí.';
export const ORDERS_NO_MATCH = 'No hay pedidos con ese filtro.';
export const ORDERS_PAGE_SIZE = DEFAULT_PAGE_SIZE;
/** Espera de la búsqueda por texto antes de pedir la lista (CA1). */
export const SEARCH_DEBOUNCE_MS = 250;

/** Mensaje de éxito de una cancelación (CA2). */
export function orderCancelledMessage(order: Order): string {
  return `Pedido #${order.id} cancelado. Las cantidades volvieron a sus lotes.`;
}

const EMPTY_SUMMARY: OrderSummary = { all: 0, PENDING: 0, IN_PREPARATION: 0, READY: 0, DELIVERED: 0, CLOSED: 0, CANCELLED: 0 };

/**
 * `/pedidos` "Pedidos" (ADMIN y CASHIER, D38, D39). HU-10: "Nuevo pedido"; HU-11: pills de estado con contadores,
 * búsqueda por número, mesa o mesero, listado paginado y "Cancelar" (con motivo) solo en pedidos pendientes.
 */
@Component({
  selector: 'app-orders-page',
  imports: [OrderDialog, CancelOrderDialog, Pagination],
  templateUrl: './orders-page.html',
  styleUrl: './orders-page.scss',
})
export class OrdersPage {
  readonly #orders = inject(OrdersService);
  readonly #toast = inject(ToastService);

  protected readonly creating = signal(false);
  protected readonly cancelling = signal<Order | null>(null);
  protected readonly filters = ORDER_STATUS_FILTERS;
  protected readonly statusLabel = ORDER_STATUS_LABEL;
  protected readonly statusPill = ORDER_STATUS_PILL;
  protected readonly pageSize = ORDERS_PAGE_SIZE;
  protected readonly formatDate = formatBusinessDateTime;
  protected readonly formatMoney = formatCop;
  protected readonly emptyText = ORDERS_EMPTY;
  protected readonly noMatchText = ORDERS_NO_MATCH;

  protected readonly q = signal('');
  protected readonly status = signal<OrderStatusFilter>(null);
  protected readonly page = signal(0);
  protected readonly rows = signal<readonly Order[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(false);
  protected readonly loaded = signal(false);
  protected readonly summary = signal<OrderSummary>(EMPTY_SUMMARY);

  #requestId = 0;
  #searchTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.#searchTimer));
    void this.reload();
  }

  /** Recarga lista y contadores (tras crear o cancelar). */
  protected reload(): Promise<void> {
    return Promise.all([this.load(), this.loadSummary()]).then(() => undefined);
  }

  /** CA1: una pill filtra por estado y vuelve a la primera página. */
  protected onStatus(status: OrderStatusFilter): void {
    this.status.set(status);
    this.page.set(0);
    void this.load();
  }

  /** CA1: búsqueda con espera de {@link SEARCH_DEBOUNCE_MS} ms; los contadores respetan la búsqueda. */
  protected onSearch(value: string): void {
    this.q.set(value);
    clearTimeout(this.#searchTimer);
    this.#searchTimer = setTimeout(() => {
      this.page.set(0);
      void this.reload();
    }, SEARCH_DEBOUNCE_MS);
  }

  protected onPage(page: number): void {
    this.page.set(page);
    void this.load();
  }

  protected openCreate(): void {
    this.creating.set(true);
  }

  protected closeCreate(): void {
    this.creating.set(false);
  }

  protected onCreated(_order: Order): void {
    this.creating.set(false);
    void this.reload();
  }

  protected askCancel(order: Order): void {
    this.cancelling.set(order);
  }

  protected closeCancel(): void {
    this.cancelling.set(null);
    // Un 409 (ya no está pendiente) deja la lista desactualizada: se refresca al cerrar.
    void this.reload();
  }

  protected onCancelled(order: Order): void {
    this.cancelling.set(null);
    this.#toast.success(orderCancelledMessage(order));
    void this.reload();
  }

  protected async load(): Promise<void> {
    const requestId = ++this.#requestId;
    this.loading.set(true);
    try {
      const result = await this.#orders.list({
        status: this.status(),
        q: this.q(),
        page: this.page(),
        size: ORDERS_PAGE_SIZE,
      });
      if (requestId !== this.#requestId) {
        return;
      }
      const lastPage = Math.max(0, Math.ceil(result.totalElements / ORDERS_PAGE_SIZE) - 1);
      if (result.content.length === 0 && result.totalElements > 0 && this.page() > lastPage) {
        this.page.set(lastPage);
        void this.load();
        return;
      }
      this.rows.set(result.content);
      this.totalElements.set(result.totalElements);
      this.loaded.set(true);
    } catch (e) {
      if (requestId === this.#requestId) {
        this.#toast.error(problemMessage(e));
      }
    } finally {
      if (requestId === this.#requestId) {
        this.loading.set(false);
      }
    }
  }

  protected async loadSummary(): Promise<void> {
    try {
      this.summary.set(await this.#orders.summary(this.q()));
    } catch {
      // La lista ya informa el error de carga; los contadores conservan el último valor conocido.
    }
  }
}
