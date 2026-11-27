import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DiningTable, QrSheetEntry } from './tables.model';

export const TABLES_URL = '/api/v1/tables';
export const QR_SHEET_URL = '/api/v1/tables/qr-sheet';

/** PATCH /api/v1/tables/{id}/active. */
export function tableActiveUrl(id: number): string {
  return `${TABLES_URL}/${id}/active`;
}

/** POST /api/v1/tables/{id}/rotate-token. */
export function rotateTokenUrl(id: number): string {
  return `${TABLES_URL}/${id}/rotate-token`;
}

/** Acceso a la API de mesas y QR (LCV-HU-07). */
@Service()
export class TablesService {
  readonly #http = inject(HttpClient);

  list(): Promise<DiningTable[]> {
    return firstValueFrom(this.#http.get<DiningTable[]>(TABLES_URL));
  }

  /** CA1: el backend genera el token HMAC y el PIN. */
  create(number: number): Promise<unknown> {
    return firstValueFrom(this.#http.post(TABLES_URL, { number }));
  }

  /** CA1: activar o desactivar. */
  setActive(id: number, active: boolean): Promise<unknown> {
    return firstValueFrom(this.#http.patch(tableActiveUrl(id), { active }));
  }

  /** CA3: nuevo token y PIN; el QR anterior deja de servir. */
  rotateToken(id: number): Promise<unknown> {
    return firstValueFrom(this.#http.post(rotateTokenUrl(id), null));
  }

  /** CA2: datos para dibujar los QR de las mesas activas. */
  qrSheet(): Promise<QrSheetEntry[]> {
    return firstValueFrom(this.#http.get<QrSheetEntry[]>(QR_SHEET_URL));
  }
}
