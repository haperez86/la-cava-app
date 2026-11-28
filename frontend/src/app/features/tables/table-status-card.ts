import { Component, computed, input } from '@angular/core';
import { Icon } from '../../shared/icons/icon';
import { CopCurrencyPipe } from '../../shared/pipes/cop-currency.pipe';
import {
  NO_OPEN_ORDERS,
  TABLE_STAGE_LABEL,
  TABLE_STAGE_PILL,
  TABLE_STATUS_LABEL,
  TABLE_STATUS_PILL,
  ordersLabel,
} from './table-status';
import { DiningTable } from './tables.model';

/**
 * Tarjeta presentacional del estado de una mesa (LCV-HU-09 CA1): número, pill Disponible/Ocupada y, si está ocupada,
 * la etapa más urgente resaltada con el número de pedidos, el total y el mesero. No es interactiva: quien la usa
 * decide si la envuelve en un botón (cajero) o la deja fija (mesero, HU-08 CA4). Solo usa elementos de frase para
 * poder ir dentro de un `<button>`.
 *
 * Contenido proyectado: `[tileFlag]` va junto a las pills (p. ej. "Inactiva" del administrador) y el resto al pie
 * (acciones). La tarjeta es un contenedor de consultas (`container-type: inline-size`) para esas acciones.
 */
@Component({
  selector: 'app-table-status-card',
  imports: [CopCurrencyPipe, Icon],
  templateUrl: './table-status-card.html',
  styleUrl: './table-status-card.scss',
  host: {
    class: 'table-tile',
    '[class.occupied]': 'occupied()',
    '[class.ready-tile]': "table().stage === 'READY' && occupied()",
    '[class.off]': 'inactive()',
  },
})
export class TableStatusCard {
  readonly table = input.required<DiningTable>();
  /** Mesa desactivada (vista del administrador): se atenúa. */
  readonly inactive = input(false);

  protected readonly occupied = computed(() => this.table().status === 'OCCUPIED');
  protected readonly statusLabel = computed(() => TABLE_STATUS_LABEL[this.table().status]);
  protected readonly statusPill = computed(() => TABLE_STATUS_PILL[this.table().status]);
  protected readonly stage = computed(() => {
    const stage = this.table().stage;
    return this.occupied() && stage ? { label: TABLE_STAGE_LABEL[stage], pill: TABLE_STAGE_PILL[stage] } : null;
  });
  protected readonly orders = computed(() => ordersLabel(this.table().openOrders));
  protected readonly empty = NO_OPEN_ORDERS;
}
