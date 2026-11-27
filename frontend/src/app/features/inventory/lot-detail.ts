import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { problemMessage } from '../../core/http/problem';
import { formatBusinessDate, formatBusinessDateTime, formatIsoDate } from '../../shared/dates/business-date';
import {
  InventoryRow,
  Lot,
  LOT_STATUS_PILLS,
  Movement,
  MOVEMENT_LABELS,
  signedQuantity,
} from './inventory.model';
import { InventoryService } from './inventory.service';

const DEFAULT_ICON = '🍽';

/**
 * Detalle de un producto del inventario (LCV-HU-06 CA4): sus lotes (más próximos a vencer primero)
 * y sus movimientos recientes. Hoja inferior en móvil y modal centrado desde tablet.
 */
@Component({
  selector: 'app-lot-detail',
  imports: [CdkTrapFocus],
  templateUrl: './lot-detail.html',
  styleUrl: './lot-detail.scss',
})
export class LotDetail implements OnInit {
  readonly #inventory = inject(InventoryService);

  readonly row = input.required<InventoryRow>();
  readonly closed = output<void>();

  protected readonly defaultIcon = DEFAULT_ICON;
  protected readonly lotPills = LOT_STATUS_PILLS;
  protected readonly movementLabels = MOVEMENT_LABELS;
  protected readonly formatDate = formatIsoDate;
  protected readonly formatInstantDate = formatBusinessDate;
  protected readonly formatDateTime = formatBusinessDateTime;
  protected readonly signed = signedQuantity;

  protected readonly lots = signal<readonly Lot[]>([]);
  protected readonly movements = signal<readonly Movement[]>([]);
  protected readonly loadingLots = signal(true);
  protected readonly loadingMovements = signal(true);
  protected readonly lotsError = signal('');
  protected readonly movementsError = signal('');

  /** Lotes ordenados por vencimiento (FEFO), como la demo. */
  protected readonly sortedLots = computed(() =>
    [...this.lots()].sort((a, b) => a.expiresOn.localeCompare(b.expiresOn) || a.id - b.id),
  );

  /**
   * Vigente del producto: suma de sus lotes no vencidos una vez cargados (sigue al día tras un `refresh()`
   * aunque la fila ya no esté en la página de la tabla); mientras tanto, el de la fila.
   */
  protected readonly available = computed(() =>
    this.loadingLots() || this.lotsError()
      ? this.row().available
      : this.lots()
          .filter((lot) => lot.status === 'ACTIVE' || lot.status === 'EXPIRING')
          .reduce((sum, lot) => sum + lot.quantityAvailable, 0),
  );

  #lotsRequest = 0;
  #movementsRequest = 0;

  ngOnInit(): void {
    this.refresh();
  }

  /** Vuelve a pedir lotes y movimientos (el padre lo llama tras una entrada, salida o baja). */
  refresh(): void {
    const productId = this.row().productId;
    void this.#loadLots(productId);
    void this.#loadMovements(productId);
  }

  async #loadLots(productId: number): Promise<void> {
    const request = ++this.#lotsRequest;
    try {
      const lots = await this.#inventory.productLots(productId);
      if (request === this.#lotsRequest) {
        this.lots.set(lots);
        this.lotsError.set('');
      }
    } catch (e) {
      if (request === this.#lotsRequest) {
        this.lotsError.set(problemMessage(e));
      }
    } finally {
      if (request === this.#lotsRequest) {
        this.loadingLots.set(false);
      }
    }
  }

  async #loadMovements(productId: number): Promise<void> {
    const request = ++this.#movementsRequest;
    try {
      const movements = await this.#inventory.recentMovements(productId);
      if (request === this.#movementsRequest) {
        this.movements.set(movements);
        this.movementsError.set('');
      }
    } catch (e) {
      if (request === this.#movementsRequest) {
        this.movementsError.set(problemMessage(e));
      }
    } finally {
      if (request === this.#movementsRequest) {
        this.loadingMovements.set(false);
      }
    }
  }
}
