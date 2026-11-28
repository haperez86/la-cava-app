import { Component, computed, inject, signal } from '@angular/core';
import { problemMessage } from '../../core/http/problem';
import { refreshOnReconnect } from '../../core/notifications/sse-client';
import { ToastService } from '../../core/notifications/toast.service';
import { HtmlPrinter, PRINT_ERROR } from '../../core/print/print-html';
import { formatBusinessDateTime } from '../../shared/dates/business-date';
import { ORDER_STATUS_LABEL, ORDER_STATUS_PILL, Order, OrderStatus } from '../orders/order.model';
import { comandaHtml } from './comanda-ticket';
import { KitchenService } from './kitchen.service';

export const KITCHEN_EMPTY = 'No hay comandas por preparar.';

/** Columnas del tablero, en el orden de la API (D40). */
export const KITCHEN_COLUMNS: readonly { readonly status: OrderStatus; readonly title: string }[] = [
  { status: 'PENDING', title: 'Pendientes' },
  { status: 'IN_PREPARATION', title: 'En preparación' },
  { status: 'READY', title: 'Listos' },
];

/** Texto del botón de listo (CA2). */
export const readyLabel = (order: Order): string => `Listo · avisar a ${order.waiterName}`;

/** Toast tras "Reenviar aviso" (HU-14 CA4). */
export const resentMessage = (order: Order): string => `Aviso reenviado a ${order.waiterName}`;

/**
 * `/preparacion` "Comandas" (ADMIN y KITCHEN, D25, D40): columnas por estado (una sola columna en pantallas angostas),
 * "Iniciar preparación", "Listo · avisar a <mesero>" e "Imprimir comanda". Refresco manual hasta HU-15 (SSE).
 */
@Component({
  selector: 'app-kitchen-page',
  templateUrl: './kitchen-page.html',
  styleUrl: './kitchen-page.scss',
})
export class KitchenPage {
  readonly #kitchen = inject(KitchenService);
  readonly #toast = inject(ToastService);
  readonly #printer = inject(HtmlPrinter);

  protected readonly columns = KITCHEN_COLUMNS;
  protected readonly statusLabel = ORDER_STATUS_LABEL;
  protected readonly statusPill = ORDER_STATUS_PILL;
  protected readonly emptyText = KITCHEN_EMPTY;
  protected readonly formatTime = formatBusinessDateTime;
  protected readonly readyLabel = readyLabel;

  protected readonly orders = signal<readonly Order[]>([]);
  protected readonly loaded = signal(false);
  protected readonly loading = signal(false);
  /** Pedidos con una acción en curso: evitan el doble clic (un segundo `start` daría 409). */
  protected readonly busy = signal<ReadonlySet<number>>(new Set());

  protected readonly byStatus = computed(() => {
    const groups = new Map<OrderStatus, Order[]>(KITCHEN_COLUMNS.map((c) => [c.status, []]));
    for (const order of this.orders()) {
      groups.get(order.status)?.push(order);
    }
    return groups;
  });

  protected readonly isEmpty = computed(() => this.loaded() && this.orders().length === 0);

  constructor() {
    void this.reload();
    // HU-13 CA2: tras una caída del canal SSE los datos pueden estar viejos.
    refreshOnReconnect(() => void this.reload());
  }

  /** CA1: vuelve a pedir el tablero (también tras un 409 por un cambio concurrente). */
  protected async reload(): Promise<void> {
    this.loading.set(true);
    try {
      this.orders.set(await this.#kitchen.board());
      this.loaded.set(true);
    } catch (e) {
      this.#toast.error(problemMessage(e));
    } finally {
      this.loading.set(false);
    }
  }

  protected start(order: Order): Promise<void> {
    return this.act(order, () => this.#kitchen.start(order.id));
  }

  protected ready(order: Order): Promise<void> {
    return this.act(order, () => this.#kitchen.ready(order.id));
  }

  /** HU-14 CA4: crea un aviso nuevo para el mesero; un 409 (ya no está listo) refresca el tablero. */
  protected async notifyAgain(order: Order): Promise<void> {
    if (this.isBusy(order)) {
      return;
    }
    this.busy.update((set) => new Set(set).add(order.id));
    try {
      await this.#kitchen.notifyAgain(order.id);
      this.#toast.success(resentMessage(order));
    } catch (e) {
      this.#toast.error(problemMessage(e));
      await this.reload();
    } finally {
      this.busy.update((set) => {
        const next = new Set(set);
        next.delete(order.id);
        return next;
      });
    }
  }

  /** CA3: tirilla de 80 mm con iframe oculto. */
  protected async print(order: Order): Promise<void> {
    try {
      await this.#printer.print(comandaHtml(order));
    } catch {
      this.#toast.error(PRINT_ERROR);
    }
  }

  protected isBusy(order: Order): boolean {
    return this.busy().has(order.id);
  }

  private async act(order: Order, call: () => Promise<Order>): Promise<void> {
    if (this.isBusy(order)) {
      return;
    }
    this.busy.update((set) => new Set(set).add(order.id));
    try {
      await call();
    } catch (e) {
      this.#toast.error(problemMessage(e));
    } finally {
      this.busy.update((set) => {
        const next = new Set(set);
        next.delete(order.id);
        return next;
      });
    }
    // Éxito o 409 (otro usuario ya lo movió): el tablero se vuelve a pedir.
    await this.reload();
  }
}
