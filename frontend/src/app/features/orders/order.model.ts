/** Estados del pedido (especificación §3.2). En HU-10 solo se crea en `PENDING`. */
export type OrderStatus = 'PENDING' | 'IN_PREPARATION' | 'READY' | 'DELIVERED' | 'CANCELLED' | 'CLOSED';

/** Texto de la pill de cada estado (siempre con texto, nunca solo color). */
export const ORDER_STATUS_LABEL: Readonly<Record<OrderStatus, string>> = {
  PENDING: 'Pendiente',
  IN_PREPARATION: 'En preparación',
  READY: 'Listo',
  DELIVERED: 'Entregado',
  CANCELLED: 'Cancelado',
  CLOSED: 'Cerrado',
};

/** Clase de pill del skill lacava-design para cada estado. */
export const ORDER_STATUS_PILL: Readonly<Record<OrderStatus, string>> = {
  PENDING: 'busy',
  IN_PREPARATION: 'busy',
  READY: 'ready',
  DELIVERED: '',
  CANCELLED: 'danger',
  CLOSED: '',
};

export interface OrderItem {
  readonly id: number;
  readonly productId: number;
  readonly productName: string;
  readonly unitPrice: number;
  readonly quantity: number;
  readonly note: string | null;
  readonly subtotal: number;
}

/** Respuesta de POST /api/v1/orders (201) y pedidos de la cuenta de la mesa. */
export interface Order {
  readonly id: number;
  readonly tableId: number;
  readonly tableNumber: number;
  readonly waiterId: number;
  readonly waiterName: string;
  readonly status: OrderStatus;
  readonly note: string | null;
  readonly createdAt: string;
  readonly items: readonly OrderItem[];
  readonly total: number;
  /** Motivo y fecha de la cancelación (HU-11, D39); `null` o ausentes si el pedido no está cancelado. */
  readonly cancelReason?: string | null;
  readonly cancelledAt?: string | null;
}

/** Filtro de estado del listado; `null` = todos. */
export type OrderStatusFilter = OrderStatus | null;

/** Respuesta de GET /api/v1/orders/summary: contadores con los mismos `q` e ignorando el estado (D39). */
export interface OrderSummary {
  readonly all: number;
  readonly PENDING: number;
  readonly IN_PREPARATION: number;
  readonly READY: number;
  readonly DELIVERED: number;
  readonly CLOSED: number;
  readonly CANCELLED: number;
}

/** Pills del listado, en orden: clave del contador y estado que filtran. */
export const ORDER_STATUS_FILTERS: readonly {
  readonly key: keyof OrderSummary;
  readonly status: OrderStatusFilter;
  readonly label: string;
}[] = [
  { key: 'all', status: null, label: 'Todos' },
  { key: 'PENDING', status: 'PENDING', label: 'Pendientes' },
  { key: 'IN_PREPARATION', status: 'IN_PREPARATION', label: 'En preparación' },
  { key: 'READY', status: 'READY', label: 'Listos' },
  { key: 'DELIVERED', status: 'DELIVERED', label: 'Entregados' },
  { key: 'CLOSED', status: 'CLOSED', label: 'Cerrados' },
  { key: 'CANCELLED', status: 'CANCELLED', label: 'Cancelados' },
];

/** Parámetros de GET /api/v1/orders. */
export interface OrderQuery {
  readonly status: OrderStatusFilter;
  readonly q: string;
  readonly page: number;
  readonly size: number;
}

export interface CreateOrderItem {
  readonly productId: number;
  readonly quantity: number;
  readonly note?: string;
}

/**
 * Cuerpo de POST /api/v1/orders. El mesero envía `tableCode` (comprobante `tablePass` de `resolve`, D37) y no
 * `waiterId`; el cajero y el administrador envían `waiterId` (D38).
 */
export interface CreateOrderRequest {
  readonly tableId: number;
  readonly waiterId?: number;
  readonly items: readonly CreateOrderItem[];
  readonly note?: string;
  readonly tableCode?: string;
}

/** Fila de GET /api/v1/users/waiters (A, C). */
export interface Waiter {
  readonly id: number;
  readonly fullName: string;
}
