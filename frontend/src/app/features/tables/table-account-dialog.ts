import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Component, DestroyRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { problemMessage } from '../../core/http/problem';
import { ToastService } from '../../core/notifications/toast.service';
import { OrderDialog, OrderTable } from '../orders/order-dialog';
import { TableAccountOrders } from './table-account-orders';
import { TABLE_STATUS_LABEL, TABLE_STATUS_PILL } from './table-status';
import { TableAccount } from './tables.model';
import { TablesService } from './tables.service';

export const EMPTY_TABLE_ACCOUNT = 'Aún no hay pedidos en esta mesa.';

/** "Mesa disponible" / "1 pedido abierto" / "3 pedidos abiertos". */
export function accountSummary(count: number): string {
  if (!count) {
    return 'Mesa disponible';
  }
  return count === 1 ? '1 pedido abierto' : `${count} pedidos abiertos`;
}

/**
 * Cuenta de la mesa para el cajero y el administrador (LCV-HU-09): pedidos abiertos con ítems y total
 * (`GET /tables/{id}/account`) y "＋ Nuevo pedido", que abre el modal de pedido en modo `staff` con la mesa fija
 * (solo se elige el mesero). Tras crear el pedido vuelve a la cuenta al día y avisa con `changed`.
 */
@Component({
  selector: 'app-table-account-dialog',
  imports: [CdkTrapFocus, OrderDialog, TableAccountOrders],
  templateUrl: './table-account-dialog.html',
  styleUrl: './table-account-dialog.scss',
  host: { '(document:keydown.escape)': 'onEscape()' },
})
export class TableAccountDialog implements OnInit {
  readonly #tables = inject(TablesService);
  readonly #toast = inject(ToastService);

  readonly table = input.required<OrderTable>();
  readonly closed = output<void>();
  /** Se creó un pedido en la mesa: la grilla debe refrescarse. */
  readonly changed = output<void>();

  protected readonly account = signal<TableAccount | null>(null);
  protected readonly loading = signal(true);
  /** El modal de pedido está a la vista (la cuenta se oculta mientras tanto). */
  protected readonly ordering = signal(false);
  protected readonly emptyText = EMPTY_TABLE_ACCOUNT;

  readonly #status = computed(() => (this.account()?.orders.length ? 'OCCUPIED' : 'FREE'));
  protected readonly statusLabel = computed(() => TABLE_STATUS_LABEL[this.#status()]);
  protected readonly statusPill = computed(() => TABLE_STATUS_PILL[this.#status()]);
  protected readonly summary = computed(() => {
    const count = this.account()?.orders.length ?? 0;
    return accountSummary(count);
  });

  #destroyed = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => (this.#destroyed = true));
  }

  ngOnInit(): void {
    void this.#load();
  }

  protected close(): void {
    this.closed.emit();
  }

  /** Escape cierra la cuenta; con el pedido abierto, lo cierra el propio modal de pedido. */
  protected onEscape(): void {
    if (!this.ordering()) {
      this.close();
    }
  }

  protected newOrder(): void {
    this.ordering.set(true);
  }

  protected closeOrder(): void {
    this.ordering.set(false);
  }

  protected onOrderCreated(): void {
    this.ordering.set(false);
    this.changed.emit();
    void this.#load();
  }

  async #load(): Promise<void> {
    this.loading.set(true);
    try {
      const response = await this.#tables.account(this.table().id);
      if (!this.#destroyed) {
        this.account.set(response.account);
      }
    } catch (e) {
      if (!this.#destroyed) {
        this.#toast.error(problemMessage(e));
      }
    } finally {
      this.loading.set(false);
    }
  }
}
