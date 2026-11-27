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

/** Producto del selector con su existencia vigente (`available` de GET /api/v1/products). */
export interface StockProductOption extends ProductOption {
  readonly available: number;
}

/** Tipos de salida manual (D33). */
export type OutputType = 'WASTE_OUT' | 'DAMAGED_OUT' | 'INTERNAL_USE_OUT' | 'ADJUSTMENT_OUT';

/** Etiquetas en español de cada tipo de salida, en el orden del selector. */
export const OUTPUT_TYPES: readonly { readonly value: OutputType; readonly label: string }[] = [
  { value: 'WASTE_OUT', label: 'Merma' },
  { value: 'DAMAGED_OUT', label: 'Producto dañado' },
  { value: 'INTERNAL_USE_OUT', label: 'Consumo interno' },
  { value: 'ADJUSTMENT_OUT', label: 'Ajuste' },
];

/** Cuerpo de POST /api/v1/inventory/outputs (LCV-HU-05 CA2). */
export interface OutputRequest {
  readonly productId: number;
  readonly quantity: number;
  readonly type: OutputType;
  readonly note: string;
}

/** Cantidad tomada de un lote por el consumo FEFO (LCV-HU-05 CA1). */
export interface ConsumedLot {
  readonly lotId: number;
  /** 'YYYY-MM-DD'. */
  readonly expiresOn: string;
  readonly quantity: number;
}

/** Respuesta de POST /api/v1/inventory/outputs. */
export interface OutputResult {
  readonly productId: number;
  readonly productName: string;
  readonly type: OutputType;
  readonly quantity: number;
  readonly consumed: readonly ConsumedLot[];
  readonly available: number;
}

/** Respuesta de POST /api/v1/inventory/expired/write-off (LCV-HU-05 CA3). */
export interface WriteOffResult {
  readonly lots: number;
  readonly units: number;
}
