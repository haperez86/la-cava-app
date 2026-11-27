import { Order } from '../orders/order.model';

/** Mesa de GET /api/v1/tables (HU-07; los campos de estado llegan con HU-09, D35). */
export interface DiningTable {
  readonly id: number;
  readonly number: number;
  readonly active: boolean;
}

/** Fila de GET /api/v1/tables/qr-sheet: solo mesas activas; `code` = `LACAVA:MESA:<n>:<token>` (D12). */
export interface QrSheetEntry {
  readonly number: number;
  readonly code: string;
  readonly pin: string;
}

/** Cuerpo de POST /api/v1/tables/resolve: contenido del QR o número con PIN (texto de 4 dígitos, D12). */
export type ResolveTableRequest = { readonly code: string } | { readonly number: number; readonly pin: string };

/** Cuenta abierta de la mesa: sus pedidos abiertos con ítems y el total (D38). */
export interface TableAccount {
  readonly orders: readonly Order[];
  readonly total: number;
}

/**
 * Respuesta de POST /api/v1/tables/resolve (LCV-HU-08). `tablePass` es el comprobante firmado y de vida corta que
 * el mesero envía como `tableCode` al crear el pedido; vence en `passExpiresAt` (ISO 8601, D37).
 */
export interface ResolvedTable {
  readonly id: number;
  readonly number: number;
  readonly tablePass: string;
  readonly passExpiresAt: string;
  readonly account: TableAccount;
}
