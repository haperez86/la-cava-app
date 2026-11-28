import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DiningTable, QrSheetEntry, ResolveTableRequest, ResolvedTable, TableAccountResponse } from './tables.model';

export const TABLES_URL = '/api/v1/tables';
export const QR_SHEET_URL = '/api/v1/tables/qr-sheet';
export const RESOLVE_TABLE_URL = '/api/v1/tables/resolve';

/** PATCH /api/v1/tables/{id}/active. */
export function tableActiveUrl(id: number): string {
  return `${TABLES_URL}/${id}/active`;
}

/** POST /api/v1/tables/{id}/rotate-token. */
export function rotateTokenUrl(id: number): string {
  return `${TABLES_URL}/${id}/rotate-token`;
}

/** GET /api/v1/tables/{id}/account. */
export function tableAccountUrl(id: number): string {
  return `${TABLES_URL}/${id}/account`;
}

/** Acceso a la API de mesas y QR (LCV-HU-07, LCV-HU-08 y LCV-HU-09). */
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

  /** HU-08: carga la mesa con su cuenta a partir del QR o del número y el PIN; 404 "Código de mesa inválido". */
  resolve(request: ResolveTableRequest): Promise<ResolvedTable> {
    return firstValueFrom(this.#http.post<ResolvedTable>(RESOLVE_TABLE_URL, request));
  }

  /** HU-09 (A, C): cuenta abierta de la mesa con sus pedidos, ítems y total. */
  account(id: number): Promise<TableAccountResponse> {
    return firstValueFrom(this.#http.get<TableAccountResponse>(tableAccountUrl(id)));
  }
}
