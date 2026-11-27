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
  /** Solo los activos se ofrecen en los selectores de entrada y salida. */
  readonly active: boolean;
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

// ---------------------------------------------------------------------------------------------------------------
// LCV-HU-06 · Vista de inventario y alertas (D34)
// ---------------------------------------------------------------------------------------------------------------

/** Estado de una fila de inventario, con prioridad OUT_OF_SEASON → OUT → LOW → EXPIRING → AVAILABLE (D34). */
export type InventoryState = 'AVAILABLE' | 'LOW' | 'OUT' | 'EXPIRING' | 'OUT_OF_SEASON';

/** Valor del filtro `state`: un estado de fila o la marca aparte `HAS_EXPIRED` (conjuntos que se solapan). */
export type InventoryStateFilter = InventoryState | 'HAS_EXPIRED';

/** Fila de GET /api/v1/inventory. */
export interface InventoryRow {
  readonly productId: number;
  readonly name: string;
  readonly icon: string | null;
  readonly categoryId: number;
  readonly categoryName: string;
  readonly seasonName: string | null;
  /** 'YYYY-MM-DD'. */
  readonly seasonUntil: string | null;
  readonly available: number;
  readonly minStock: number;
  /** 'YYYY-MM-DD' del próximo lote vigente que vence. */
  readonly nextExpiresOn: string | null;
  /** Días hasta `nextExpiresOn`, calculados por el backend con su `Clock`. */
  readonly daysToExpiry: number | null;
  readonly expiredUnits: number;
  readonly state: InventoryState;
  readonly hasExpired: boolean;
}

/** Respuesta de GET /api/v1/inventory/summary (CA1) con las temporadas del filtro. */
export interface InventorySummary {
  readonly outOfStock: number;
  readonly lowStock: number;
  readonly expiring: number;
  readonly expiredUnits: number;
  readonly seasons: readonly string[];
}

/** Parámetros de GET /api/v1/inventory. */
export interface InventoryQuery {
  readonly q: string;
  readonly categoryId: number | null;
  readonly state: InventoryStateFilter | null;
  readonly season: string | null;
  readonly page: number;
  readonly size: number;
}

/** Categoría para el filtro (GET /api/v1/categories). */
export interface CategoryOption {
  readonly id: number;
  readonly name: string;
}

/** Tipos de movimiento de inventario. */
export type MovementType = 'LOT_IN' | 'SALE' | 'ORDER_CANCEL_RETURN' | 'EXPIRED_WRITE_OFF' | OutputType;

/** Movimiento de GET /api/v1/inventory/movements (más reciente primero). */
export interface Movement {
  readonly id: number;
  /** Instante ISO-8601. */
  readonly createdAt: string;
  readonly productId: number;
  readonly productName: string;
  readonly lotId: number | null;
  readonly type: MovementType;
  /** Con signo: positiva entra, negativa sale. */
  readonly quantity: number;
  readonly note: string | null;
  readonly orderId: number | null;
  readonly userName: string | null;
}

/** Etiquetas en español de cada tipo de movimiento (CA4). */
export const MOVEMENT_LABELS: Readonly<Record<MovementType, string>> = {
  LOT_IN: 'Entrada',
  SALE: 'Venta',
  ORDER_CANCEL_RETURN: 'Devolución por cancelación',
  WASTE_OUT: 'Merma',
  DAMAGED_OUT: 'Producto dañado',
  INTERNAL_USE_OUT: 'Consumo interno',
  ADJUSTMENT_OUT: 'Ajuste',
  EXPIRED_WRITE_OFF: 'Baja por vencimiento',
};

/** Pill de un estado: texto (el color nunca es la única señal) y variante de estilo. */
export interface PillStyle {
  readonly label: string;
  readonly tone: '' | 'busy' | 'danger' | 'season' | 'neutral';
}

/** Pill de cada estado de fila (CA2). */
export const STATE_PILLS: Readonly<Record<InventoryState, PillStyle>> = {
  AVAILABLE: { label: 'Disponible', tone: '' },
  LOW: { label: 'Stock bajo', tone: 'busy' },
  OUT: { label: 'Agotado', tone: 'danger' },
  EXPIRING: { label: 'Por vencer', tone: 'busy' },
  OUT_OF_SEASON: { label: 'Fuera de temporada', tone: 'season' },
};

/** Pill de cada estado de lote (CA4), con los textos de la demo. */
export const LOT_STATUS_PILLS: Readonly<Record<LotStatus, PillStyle>> = {
  ACTIVE: { label: 'Vigente', tone: '' },
  EXPIRING: { label: 'Vence pronto', tone: 'busy' },
  EXPIRED: { label: 'Vencido', tone: 'danger' },
  CONSUMED: { label: 'Consumido', tone: 'neutral' },
};

/** Opciones del filtro de estado (CA3); `null` = Todos, no se envía. */
export const INVENTORY_STATE_FILTERS: readonly { readonly value: InventoryStateFilter | null; readonly label: string }[] = [
  { value: null, label: 'Todos los estados' },
  { value: 'AVAILABLE', label: 'Disponible' },
  { value: 'LOW', label: 'Stock bajo' },
  { value: 'OUT', label: 'Agotado' },
  { value: 'EXPIRING', label: 'Por vencer' },
  { value: 'HAS_EXPIRED', label: 'Con vencidos' },
  { value: 'OUT_OF_SEASON', label: 'Fuera de temporada' },
];

/** Redacta `daysToExpiry` (CA2): 0 → "vence hoy", 1 → "vence mañana", N → "en N días", null → "—". */
export function expiryLabel(daysToExpiry: number | null | undefined): string {
  if (daysToExpiry === null || daysToExpiry === undefined) {
    return '—';
  }
  if (daysToExpiry === 0) {
    return 'vence hoy';
  }
  if (daysToExpiry === 1) {
    return 'vence mañana';
  }
  return `en ${daysToExpiry} días`;
}

/** Cantidad de un movimiento con signo: "+12" o "−3" (signo menos tipográfico). */
export function signedQuantity(quantity: number): string {
  return quantity > 0 ? `+${quantity}` : quantity < 0 ? `−${Math.abs(quantity)}` : '0';
}
