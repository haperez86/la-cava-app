import { HttpClient, HttpParams } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Page } from '../../shared/http/page';
import { Lot, LotInRequest, ProductOption } from './inventory.model';

export const LOTS_URL = '/api/v1/inventory/lots';
export const PRODUCTS_URL = '/api/v1/products';
/** Tamaño de página para el selector de productos de la entrada. */
export const PRODUCT_OPTIONS_SIZE = 100;

/** Acceso a la API de inventario (LCV-HU-04). */
@Service()
export class InventoryService {
  readonly #http = inject(HttpClient);

  /** Productos para el selector, ordenados por nombre. */
  async productOptions(): Promise<readonly ProductOption[]> {
    const params = new HttpParams().set('size', PRODUCT_OPTIONS_SIZE).set('sort', 'name,asc');
    const page = await firstValueFrom(this.#http.get<Page<ProductOption>>(PRODUCTS_URL, { params }));
    return page.content;
  }

  /** CA1: registra el lote; el backend crea el movimiento `LOT_IN`. */
  registerLot(body: LotInRequest): Promise<Lot> {
    return firstValueFrom(this.#http.post<Lot>(LOTS_URL, body));
  }
}
