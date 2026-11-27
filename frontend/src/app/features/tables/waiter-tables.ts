import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Component, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { problemMessage } from '../../core/http/problem';
import { ToastService } from '../../core/notifications/toast.service';
import { Icon } from '../../shared/icons/icon';
import { LoadedTable, QrScanDialog } from './qr-scan-dialog';
import { DiningTable } from './tables.model';
import { TablesService } from './tables.service';

export const SCAN_TO_OPEN = 'Escanea el QR de la mesa para abrirla.';
export const EMPTY_ACCOUNT = 'Mesa disponible para recibir un pedido.';
export const LOADED_BY: Readonly<Record<LoadedTable['origin'], string>> = {
  qr: 'Mesa cargada por QR',
  pin: 'Mesa cargada con PIN',
};
export const ORDERS_PENDING_NOTE = 'Disponible cuando se habiliten los pedidos';
const LOADED_VIBRATION_MS = 80;

/**
 * "Mis mesas" del mesero (LCV-HU-08): las mesas activas solo se muestran (CA4); se abren escaneando su QR (CA1)
 * o con número y PIN (CA2).
 */
@Component({
  selector: 'app-waiter-tables',
  imports: [CdkTrapFocus, Icon, QrScanDialog],
  templateUrl: './waiter-tables.html',
  styleUrl: './waiter-tables.scss',
  host: { '(document:keydown.escape)': 'closeTable()' },
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
  protected readonly pendingNote = ORDERS_PENDING_NOTE;
  protected readonly loadedBy = LOADED_BY;
  protected readonly loading = signal(false);
  protected readonly scanning = signal(false);
  /** Mesa cargada por QR o PIN cuyo modal está abierto. */
  protected readonly openTable = signal<LoadedTable | null>(null);
  protected readonly waiterName = computed(() => this.#auth.user()?.fullName ?? '');
  protected readonly accountNote = computed(() => {
    const orders = this.openTable()?.table.account.orders.length ?? 0;
    return orders ? `${orders} pedido(s) en la cuenta` : EMPTY_ACCOUNT;
  });

  constructor() {
    void this.load();
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
    this.openTable.set(null);
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
