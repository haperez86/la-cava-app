import { HttpClient, HttpParams } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Page } from '../../shared/http/page';
import {
  CategoryOption,
  InventoryQuery,
  InventoryRow,
  InventorySummary,
  Lot,
  LotInRequest,
  Movement,
  OutputRequest,
  OutputResult,
  StockProductOption,
  WriteOffResult,
} from './inventory.model';

export const LOTS_URL = '/api/v1/inventory/lots';
export const OUTPUTS_URL = '/api/v1/inventory/outputs';
export const WRITE_OFF_URL = '/api/v1/inventory/expired/write-off';
export const PRODUCTS_URL = '/api/v1/products';
export const INVENTORY_URL = '/api/v1/inventory';
export const SUMMARY_URL = '/api/v1/inventory/summary';
export const MOVEMENTS_URL = '/api/v1/inventory/movements';
export const CATEGORIES_URL = '/api/v1/categories';
/** Movimientos recientes que muestra el detalle de un producto (CA4). */
export const RECENT_MOVEMENTS_SIZE = 10;

/** GET /api/v1/products/{id}/lots. */
export function productLotsUrl(productId: number): string {
  return `${PRODUCTS_URL}/${productId}/lots`;
}
/** Tamaño de página para el selector de productos de la entrada y la salida. */
export const PRODUCT_OPTIONS_SIZE = 100;

/** Acceso a la API de inventario (LCV-HU-04, LCV-HU-05, LCV-HU-06). */
@Service()
export class InventoryService {
  readonly #http = inject(HttpClient);

  /** Productos activos para el selector, ordenados por nombre, con su existencia vigente. */
  async productOptions(): Promise<readonly StockProductOption[]> {
    const params = new HttpParams().set('size', PRODUCT_OPTIONS_SIZE).set('sort', 'name,asc');
    const page = await firstValueFrom(this.#http.get<Page<StockProductOption>>(PRODUCTS_URL, { params }));
    return page.content.filter((product) => product.active);
  }

  /** HU-06 CA2/CA3: filas de la vista de inventario, filtradas y paginadas por el backend. */
  list(query: InventoryQuery): Promise<Page<InventoryRow>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size).set('sort', 'name,asc');
    if (query.q.trim()) {
      params = params.set('q', query.q.trim());
    }
    if (query.categoryId !== null) {
      params = params.set('categoryId', query.categoryId);
    }
    if (query.state) {
      params = params.set('state', query.state);
    }
    if (query.season) {
      params = params.set('season', query.season);
    }
    return firstValueFrom(this.#http.get<Page<InventoryRow>>(INVENTORY_URL, { params }));
  }

  /** HU-06 CA1: contadores y temporadas del filtro. */
  summary(): Promise<InventorySummary> {
    return firstValueFrom(this.#http.get<InventorySummary>(SUMMARY_URL));
  }

  /** HU-06 CA3: categorías del filtro. */
  categories(): Promise<readonly CategoryOption[]> {
    return firstValueFrom(this.#http.get<CategoryOption[]>(CATEGORIES_URL));
  }

  /** HU-06 CA4: lotes del producto. */
  productLots(productId: number): Promise<readonly Lot[]> {
    return firstValueFrom(this.#http.get<Lot[]>(productLotsUrl(productId)));
  }

  /** HU-06 CA4: movimientos recientes del producto (más reciente primero). */
  async recentMovements(productId: number): Promise<readonly Movement[]> {
    const params = new HttpParams().set('productId', productId).set('page', 0).set('size', RECENT_MOVEMENTS_SIZE);
    const page = await firstValueFrom(this.#http.get<Page<Movement>>(MOVEMENTS_URL, { params }));
    return page.content;
  }

  /** HU-04 CA1: registra el lote; el backend crea el movimiento `LOT_IN`. */
  registerLot(body: LotInRequest): Promise<Lot> {
    return firstValueFrom(this.#http.post<Lot>(LOTS_URL, body));
  }

  /** HU-05 CA1/CA2: salida manual; el backend consume FEFO y devuelve el reparto por lote. */
  registerOutput(body: OutputRequest): Promise<OutputResult> {
    return firstValueFrom(this.#http.post<OutputResult>(OUTPUTS_URL, body));
  }

  /** HU-05 CA3: deja en 0 los lotes vencidos (un movimiento por lote). */
  writeOffExpired(): Promise<WriteOffResult> {
    return firstValueFrom(this.#http.post<WriteOffResult>(WRITE_OFF_URL, null));
  }
}
