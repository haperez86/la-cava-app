import { Order } from '../orders/order.model';

/** Estado de la mesa (LCV-HU-09): ocupada si tiene pedidos abiertos. */
export type TableStatus = 'FREE' | 'OCCUPIED';

/** Etapa más urgente de los pedidos abiertos: READY > IN_PREPARATION > PENDING > SERVED (LCV-HU-09 CA1). */
export type TableStage = 'READY' | 'IN_PREPARATION' | 'PENDING' | 'SERVED';

/**
 * Mesa de GET /api/v1/tables (HU-07) con su estado (HU-09, D35): `stage`, `waiterName` son `null` si está libre;
 * `total` es el de sus pedidos abiertos.
 */
export interface DiningTable {
  readonly id: number;
  readonly number: number;
  readonly active: boolean;
  readonly status: TableStatus;
  readonly stage: TableStage | null;
  readonly openOrders: number;
  readonly total: number;
  readonly waiterName: string | null;
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

/** Respuesta de GET /api/v1/tables/{id}/account (A, C). */
export interface TableAccountResponse {
  readonly id: number;
  readonly number: number;
  readonly account: TableAccount;
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
