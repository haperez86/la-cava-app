import { HttpClient, HttpParams } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Page } from '../../shared/http/page';
import { Lot, LotInRequest, OutputRequest, OutputResult, StockProductOption, WriteOffResult } from './inventory.model';

export const LOTS_URL = '/api/v1/inventory/lots';
export const OUTPUTS_URL = '/api/v1/inventory/outputs';
export const WRITE_OFF_URL = '/api/v1/inventory/expired/write-off';
export const PRODUCTS_URL = '/api/v1/products';
/** Tamaño de página para el selector de productos de la entrada y la salida. */
export const PRODUCT_OPTIONS_SIZE = 100;

/** Acceso a la API de inventario (LCV-HU-04, LCV-HU-05). */
@Service()
export class InventoryService {
  readonly #http = inject(HttpClient);

  /** Productos para el selector, ordenados por nombre, con su existencia vigente. */
  async productOptions(): Promise<readonly StockProductOption[]> {
    const params = new HttpParams().set('size', PRODUCT_OPTIONS_SIZE).set('sort', 'name,asc');
    const page = await firstValueFrom(this.#http.get<Page<StockProductOption>>(PRODUCTS_URL, { params }));
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
