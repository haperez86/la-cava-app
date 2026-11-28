import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Component, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { problemMessage } from '../../core/http/problem';
import { refreshOnReconnect } from '../../core/notifications/sse-client';
import { ToastService } from '../../core/notifications/toast.service';
import { Icon } from '../../shared/icons/icon';
import { Order } from '../orders/order.model';
import { OrderDialog, RESCAN_TABLE } from '../orders/order-dialog';
import { LoadedTable, QrScanDialog } from './qr-scan-dialog';
import { TableAccountOrders } from './table-account-orders';
import { TableLegend } from './table-legend';
import { TableStatusCard } from './table-status-card';
import { DiningTable } from './tables.model';
import { TablesService } from './tables.service';

export const SCAN_TO_OPEN = 'Escanea el QR de la mesa para abrirla.';
export const EMPTY_ACCOUNT = 'Mesa disponible para recibir un pedido.';
export const LOADED_BY: Readonly<Record<LoadedTable['origin'], string>> = {
  qr: 'Mesa cargada por QR',
  pin: 'Mesa cargada con PIN',
};
const LOADED_VIBRATION_MS = 80;

/** Verdadero si el comprobante de la mesa (D37) falta o ya venció. */
export function passExpired(passExpiresAt: string | null | undefined, now = Date.now()): boolean {
  const expiresAt = passExpiresAt ? Date.parse(passExpiresAt) : NaN;
  return !Number.isFinite(expiresAt) || expiresAt <= now;
}

/**
 * "Mis mesas" del mesero (LCV-HU-08): las mesas activas solo se muestran (CA4), con su estado y la leyenda (HU-09); se abren escaneando su QR (CA1)
 * o con número y PIN (CA2). Desde la mesa abierta se asignan pedidos con su comprobante (LCV-HU-10 CA4).
 */
@Component({
  selector: 'app-waiter-tables',
  imports: [CdkTrapFocus, Icon, OrderDialog, QrScanDialog, TableAccountOrders, TableLegend, TableStatusCard],
  templateUrl: './waiter-tables.html',
  styleUrl: './waiter-tables.scss',
  host: { '(document:keydown.escape)': 'onEscape()' },
})
export class WaiterTables {
  readonly #tables = inject(TablesService);
  readonly #toast = inject(ToastService);
  readonly #auth = inject(AuthService);

  readonly #list = signal<readonly DiningTable[]>([]);
  protected readonly tables = computed(() =>
    this.#list()
      .filter((t) => t.active)
      .sort((a, b) => a.number - b.number),
  );
  protected readonly helpText = SCAN_TO_OPEN;
  protected readonly loadedBy = LOADED_BY;
  protected readonly loading = signal(false);
  protected readonly scanning = signal(false);
  /** Mesa cargada por QR o PIN cuyo modal está abierto. */
  protected readonly openTable = signal<LoadedTable | null>(null);
  /** El modal de pedido de la mesa abierta está a la vista. */
  protected readonly ordering = signal(false);
  protected readonly waiterName = computed(() => this.#auth.user()?.fullName ?? '');
  protected readonly accountNote = computed(() => {
    const orders = this.openTable()?.table.account.orders.length ?? 0;
    return orders ? `${orders} pedido(s) en la cuenta` : EMPTY_ACCOUNT;
  });

  constructor() {
    void this.load();
    // HU-13 CA2: tras una caída del canal SSE los datos pueden estar viejos.
    refreshOnReconnect(() => void this.load());
  }

  protected openScanner(): void {
    this.scanning.set(true);
  }

  protected closeScanner(): void {
    this.scanning.set(false);
  }

  /** CA1/CA2: mesa válida → modal de la mesa con su cuenta y una vibración corta en el celular. */
  protected onResolved(loaded: LoadedTable): void {
    this.scanning.set(false);
    this.openTable.set(loaded);
    navigator.vibrate?.(LOADED_VIBRATION_MS);
  }

  protected closeTable(): void {
    this.ordering.set(false);
    this.openTable.set(null);
  }

  /** Escape cierra el modal de la mesa; con el pedido abierto, lo cierra el propio modal de pedido. */
  protected onEscape(): void {
    if (!this.ordering()) {
      this.closeTable();
    }
  }

  /** LCV-HU-10 CA4: el pedido usa el comprobante de `resolve`; si venció, hay que volver a escanear. */
  protected assignOrder(): void {
    const loaded = this.openTable();
    if (!loaded) {
      return;
    }
    if (passExpired(loaded.table.passExpiresAt)) {
      this.#toast.error(RESCAN_TABLE);
      this.closeTable();
      return;
    }
    this.ordering.set(true);
  }

  protected closeOrder(): void {
    this.ordering.set(false);
  }

  /** Pedido creado: vuelve a la mesa con su cuenta al día (el mesero no puede consultar `/account`, D38). */
  protected onOrderCreated(order: Order): void {
    this.ordering.set(false);
    this.openTable.update((loaded) => {
      if (!loaded) {
        return loaded;
      }
      const { account } = loaded.table;
      const orders = [...account.orders.filter((o) => o.id !== order.id), order];
      const total = orders.reduce((sum, o) => sum + Number(o.total), 0);
      return { ...loaded, table: { ...loaded.table, account: { orders, total } } };
    });
    // HU-09: el estado de la mesa en la grilla cambia con el nuevo pedido.
    void this.load();
  }

  /** 403 del comprobante: se cierra la mesa para que el mesero la escanee de nuevo. */
  protected onPassRejected(): void {
    this.closeTable();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.#list.set(await this.#tables.list());
    } catch (e) {
      this.#toast.error(problemMessage(e));
    } finally {
      this.loading.set(false);
    }
  }
}
