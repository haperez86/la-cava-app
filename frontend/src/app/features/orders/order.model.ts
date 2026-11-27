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
