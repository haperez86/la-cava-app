import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CreateOrderRequest, Order, Waiter } from './order.model';

export const ORDERS_URL = '/api/v1/orders';
export const WAITERS_URL = '/api/v1/users/waiters';

/** Acceso a la API de pedidos (LCV-HU-10). */
@Service()
export class OrdersService {
  readonly #http = inject(HttpClient);

  /** CA5: consume los lotes y deja el pedido en `PENDING`; 422 si un producto no alcanza. */
  create(body: CreateOrderRequest): Promise<Order> {
    return firstValueFrom(this.#http.post<Order>(ORDERS_URL, body));
  }

  /** CA4: meseros activos para asignar el pedido (A, C). */
  waiters(): Promise<Waiter[]> {
    return firstValueFrom(this.#http.get<Waiter[]>(WAITERS_URL));
  }
}
