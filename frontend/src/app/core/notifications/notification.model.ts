/** Aviso persistido del usuario (docs/03-api.md; evento SSE `notification` y `GET /notifications/unread`). */
export interface AppNotification {
  readonly id: number;
  readonly type: 'ORDER_READY' | 'ORDER_CREATED';
  readonly orderId: number;
  readonly tableNumber: number;
  readonly message: string;
  readonly createdAt: string;
  readonly readAt: string | null;
}
