import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Component, DestroyRef, inject, signal, viewChild } from '@angular/core';
import { problemMessage } from '../../core/http/problem';
import { ToastService } from '../../core/notifications/toast.service';
import { formatIsoDate } from '../../shared/dates/business-date';
import { DEFAULT_PAGE_SIZE, Pagination } from '../../shared/pagination/pagination';
import { ConfirmDialog } from './confirm-dialog';
import {
  CategoryOption,
  expiryLabel,
  INVENTORY_STATE_FILTERS,
  InventoryRow,
  InventoryStateFilter,
  InventorySummary,
  Lot,
  OutputResult,
  STATE_PILLS,
  WriteOffResult,
} from './inventory.model';
import { InventoryService } from './inventory.service';
import { EXPIRY_WARN_DAYS, InventoryStats } from './inventory-stats';
import { LotDetail } from './lot-detail';
import { LotInForm } from './lot-in-form';
import { LotOutForm } from './lot-out-form';

export const WRITE_OFF_CONFIRM = 'Se dejarán en 0 todos los lotes vencidos. ¿Continuar?';
export const NO_EXPIRED_LOTS = 'No hay lotes vencidos.';
export const INVENTORY_PAGE_SIZE = DEFAULT_PAGE_SIZE;
/** Espera de la búsqueda por texto antes de pedir la tabla (CA3). */
export const SEARCH_DEBOUNCE_MS = 250;
const DEFAULT_ICON = '🍽';

/** Mensaje de éxito de una entrada (LCV-HU-04 CA1). */
export function lotRegisteredMessage(lot: Lot): string {
  return `Lote registrado: ${lot.quantityReceived} × ${lot.productName}`;
}

/** Mensaje de éxito de una salida (LCV-HU-05 CA1/CA2): menciona el reparto si tocó varios lotes. */
export function outputRegisteredMessage(result: OutputResult): string {
  const lots = result.consumed.length;
  const split = lots > 1 ? ` (de ${lots} lotes)` : '';
  return `Salida registrada: ${result.quantity} × ${result.productName}${split}`;
}

/** Mensaje de la baja de vencidos (LCV-HU-05 CA3). */
export function writeOffMessage(result: WriteOffResult): string {
  return result.lots === 0
    ? NO_EXPIRED_LOTS
    : `Se dieron de baja ${result.lots} lote(s), ${result.units} unidad(es).`;
}

const EMPTY_SUMMARY: InventorySummary = { outOfStock: 0, lowStock: 0, expiring: 0, expiredUnits: 0, seasons: [] };

type OpenDialog = 'none' | 'entry' | 'output' | 'writeOff';

/**
 * "Inventario" (D32, D34). HU-04: "Registrar entrada"; HU-05: "Registrar salida" y "Dar de baja vencidos";
 * HU-06: contadores, filtros, tabla paginada y detalle de lotes y movimientos, que se refrescan con `reload()`.
 */
@Component({
  selector: 'app-inventory-page',
  imports: [InventoryStats, LotInForm, LotOutForm, ConfirmDialog, LotDetail, Pagination, CdkTrapFocus],
  templateUrl: './inventory-page.html',
  styleUrl: './inventory-page.scss',
  host: { '(document:keydown.escape)': 'closeForm()' },
})
export class InventoryPage {
  readonly #inventory = inject(InventoryService);
  readonly #toast = inject(ToastService);

  protected readonly writeOffConfirm = WRITE_OFF_CONFIRM;
  protected readonly open = signal<OpenDialog>('none');
  protected readonly writingOff = signal(false);
  /** Se incrementa tras cada cambio de existencias; cada incremento recarga contadores y tabla. */
  readonly reloadTick = signal(0);

  protected readonly pageSize = INVENTORY_PAGE_SIZE;
  protected readonly defaultIcon = DEFAULT_ICON;
  protected readonly warnDays = EXPIRY_WARN_DAYS;
  protected readonly stateFilters = INVENTORY_STATE_FILTERS;
  protected readonly statePills = STATE_PILLS;
  protected readonly expiryLabel = expiryLabel;
  protected readonly formatDate = formatIsoDate;

  protected readonly q = signal('');
  protected readonly categoryId = signal<number | null>(null);
  protected readonly state = signal<InventoryStateFilter | null>(null);
  protected readonly season = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly rows = signal<readonly InventoryRow[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(false);
  protected readonly summary = signal<InventorySummary>(EMPTY_SUMMARY);
  protected readonly categories = signal<readonly CategoryOption[]>([]);
  /** Producto con el detalle de lotes y movimientos abierto (CA4). */
  protected readonly detail = signal<InventoryRow | null>(null);

  /** Detalle abierto, para refrescarlo junto con la tabla. */
  private readonly detailView = viewChild(LotDetail);

  #requestId = 0;
  #searchTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.#searchTimer));
    void this.load();
    void this.loadSummary();
    void this.loadCategories();
  }

  /** Recarga contadores, tabla y el detalle abierto tras una entrada, salida o baja. */
  reload(): void {
    this.reloadTick.update((n) => n + 1);
    void this.load();
    void this.loadSummary();
    this.detailView()?.refresh();
  }

  /** CA1: un contador aplica su filtro de estado y vuelve a la primera página. */
  protected applyStat(filter: InventoryStateFilter): void {
    this.state.set(filter);
    this.page.set(0);
    void this.load();
  }

  /** CA3: búsqueda con espera de {@link SEARCH_DEBOUNCE_MS} ms para no pedir en cada tecla. */
  protected onSearch(value: string): void {
    this.q.set(value);
    clearTimeout(this.#searchTimer);
    this.#searchTimer = setTimeout(() => {
      this.page.set(0);
      void this.load();
    }, SEARCH_DEBOUNCE_MS);
  }

  protected onCategory(value: string): void {
    this.categoryId.set(value ? Number(value) : null);
    this.page.set(0);
    void this.load();
  }

  protected onState(value: string): void {
    this.state.set((value || null) as InventoryStateFilter | null);
    this.page.set(0);
    void this.load();
  }

  protected onSeason(value: string): void {
    this.season.set(value || null);
    this.page.set(0);
    void this.load();
  }

  protected onPage(page: number): void {
    this.page.set(page);
    void this.load();
  }

  protected openDetail(row: InventoryRow): void {
    this.detail.set(row);
  }

  protected closeDetail(): void {
    this.detail.set(null);
  }

  protected openEntry(): void {
    this.open.set('entry');
  }

  protected openOutput(): void {
    this.open.set('output');
  }

  protected askWriteOff(): void {
    this.open.set('writeOff');
  }

  /**
   * Escape: cierra el formulario abierto o, si no hay ninguno, el detalle.
   * La confirmación maneja su propio Escape.
   */
  protected closeForm(): void {
    if (this.open() === 'entry' || this.open() === 'output') {
      this.open.set('none');
    } else if (this.open() === 'none') {
      this.detail.set(null);
    }
  }

  protected cancelWriteOff(): void {
    this.open.set('none');
  }

  protected onSaved(lot: Lot): void {
    this.open.set('none');
    this.#toast.success(lotRegisteredMessage(lot));
    this.reload();
  }

  protected onOutputSaved(result: OutputResult): void {
    this.open.set('none');
    this.#toast.success(outputRegisteredMessage(result));
    this.reload();
  }

  protected async confirmWriteOff(): Promise<void> {
    this.writingOff.set(true);
    try {
      const result = await this.#inventory.writeOffExpired();
      this.open.set('none');
      if (result.lots === 0) {
        this.#toast.info(writeOffMessage(result));
      } else {
        this.#toast.success(writeOffMessage(result));
        this.reload();
      }
    } catch (e) {
      this.open.set('none');
      this.#toast.error(problemMessage(e));
    } finally {
      this.writingOff.set(false);
    }
  }

  protected async load(): Promise<void> {
    const requestId = ++this.#requestId;
    this.loading.set(true);
    try {
      const result = await this.#inventory.list({
        q: this.q(),
        categoryId: this.categoryId(),
        state: this.state(),
        season: this.season(),
        page: this.page(),
        size: INVENTORY_PAGE_SIZE,
      });
      if (requestId !== this.#requestId) {
        return;
      }
      // La página quedó fuera de rango (p. ej. una baja encogió la lista): ir a la última válida.
      const lastPage = Math.max(0, Math.ceil(result.totalElements / INVENTORY_PAGE_SIZE) - 1);
      if (result.content.length === 0 && result.totalElements > 0 && this.page() > lastPage) {
        this.page.set(lastPage);
        void this.load();
        return;
      }
      this.rows.set(result.content);
      this.totalElements.set(result.totalElements);
      this.#syncDetail(result.content);
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

  /** Si el producto del detalle abierto vino en la página recargada, el detalle usa su fila nueva. */
  #syncDetail(rows: readonly InventoryRow[]): void {
    const open = this.detail();
    const fresh = open && rows.find((r) => r.productId === open.productId);
    if (fresh) {
      this.detail.set(fresh);
    }
  }

  protected async loadSummary(): Promise<void> {
    try {
      this.summary.set(await this.#inventory.summary());
    } catch {
      // La tabla ya informa el error de carga; los contadores conservan el último valor conocido.
    }
  }

  protected async loadCategories(): Promise<void> {
    try {
      this.categories.set(await this.#inventory.categories());
    } catch {
      // Sin categorías el filtro queda en "Todas las categorías" y la pantalla sigue siendo usable.
    }
  }
}
