/** Estado de un lote (D32). */
export type LotStatus = 'ACTIVE' | 'EXPIRING' | 'EXPIRED' | 'CONSUMED';

/** Lote tal como lo devuelve POST /api/v1/inventory/lots (docs/03-api.md, Inventario). */
export interface Lot {
  readonly id: number;
  readonly productId: number;
  readonly productName: string;
  readonly quantityReceived: number;
  readonly quantityAvailable: number;
  /** 'YYYY-MM-DD'. */
  readonly expiresOn: string;
  readonly seasonName: string | null;
  readonly receivedAt: string;
  readonly status: LotStatus;
}

/** Cuerpo de POST /api/v1/inventory/lots (LCV-HU-04). */
export interface LotInRequest {
  readonly productId: number;
  readonly quantity: number;
  readonly expiresOn: string;
  readonly seasonName: string | null;
}

/** Lo que el selector de productos necesita de GET /api/v1/products. */
export interface ProductOption {
  readonly id: number;
  readonly name: string;
  readonly seasonName: string | null;
}
