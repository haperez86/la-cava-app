import { Component, computed, inject, signal } from '@angular/core';
import { problemMessage } from '../../core/http/problem';
import { refreshOnReconnect } from '../../core/notifications/sse-client';
import { ToastService } from '../../core/notifications/toast.service';
import { TableAccountDialog } from './table-account-dialog';
import { TableLegend } from './table-legend';
import { TableStatusCard } from './table-status-card';
import { DiningTable } from './tables.model';
import { TablesService } from './tables.service';

/**
 * Estado de las mesas para el cajero (LCV-HU-09): grilla de mesas activas con su estado y etapa (CA1) y la leyenda
 * (CA2). Tocar una mesa abre su cuenta, desde donde se crea un pedido con la mesa fija.
 */
@Component({
  selector: 'app-tables-board',
  imports: [TableAccountDialog, TableLegend, TableStatusCard],
  templateUrl: './tables-board.html',
  styleUrl: './tables-board.scss',
})
export class TablesBoard {
  readonly #tables = inject(TablesService);
  readonly #toast = inject(ToastService);

  readonly #list = signal<readonly DiningTable[]>([]);
  protected readonly tables = computed(() =>
    this.#list()
      .filter((t) => t.active)
      .sort((a, b) => a.number - b.number),
  );
  protected readonly loading = signal(false);
  /** Mesa cuya cuenta está abierta. */
  protected readonly selected = signal<DiningTable | null>(null);

  constructor() {
    void this.load();
    // HU-13 CA2: tras una caída del canal SSE los datos pueden estar viejos.
    refreshOnReconnect(() => void this.load());
  }

  protected open(table: DiningTable): void {
    this.selected.set(table);
  }

  protected closeAccount(): void {
    this.selected.set(null);
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
