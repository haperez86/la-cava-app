import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Order } from '../orders/order.model';
import { ORDERS_URL } from '../orders/orders.service';

export const KITCHEN_BOARD_URL = '/api/v1/kitchen/board';
export const startOrderUrl = (id: number) => `${ORDERS_URL}/${id}/start`;
export const readyOrderUrl = (id: number) => `${ORDERS_URL}/${id}/ready`;
export const notifyAgainUrl = (id: number) => `${ORDERS_URL}/${id}/notify-again`;

/** Acceso a la API del tablero de preparación (LCV-HU-12, D40). */
@Service()
export class KitchenService {
  readonly #http = inject(HttpClient);

  /** CA1: pedidos `PENDING`, `IN_PREPARATION` y `READY`, ya ordenados por estado y antigüedad. */
  board(): Promise<Order[]> {
    return firstValueFrom(this.#http.get<Order[]>(KITCHEN_BOARD_URL));
  }

  /** CA2: `PENDING → IN_PREPARATION` (409 si el pedido ya cambió). */
  start(id: number): Promise<Order> {
    return firstValueFrom(this.#http.post<Order>(startOrderUrl(id), null));
  }

  /** CA2: `IN_PREPARATION → READY`; el backend avisa al mesero del pedido (HU-14). */
  ready(id: number): Promise<Order> {
    return firstValueFrom(this.#http.post<Order>(readyOrderUrl(id), null));
  }

  /** HU-14 CA4: "Reenviar aviso" crea un aviso nuevo para el mesero (409 si el pedido ya no está listo). */
  notifyAgain(id: number): Promise<void> {
    return firstValueFrom(this.#http.post<void>(notifyAgainUrl(id), null));
  }
}
