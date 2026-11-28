import { HttpClient, HttpParams } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Page } from '../../shared/http/page';
import { CreateOrderRequest, Order, OrderQuery, OrderSummary, Waiter } from './order.model';

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

  /** HU-11 CA1: pedidos del más reciente al más antiguo, filtrados y paginados por el backend (A, C). */
  list(query: OrderQuery): Promise<Page<Order>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.q.trim()) {
      params = params.set('q', query.q.trim());
    }
    return firstValueFrom(this.#http.get<Page<Order>>(ORDERS_URL, { params }));
  }

  /** HU-11 CA1: contadores por estado con la misma búsqueda (ignoran el estado). */
  summary(q: string): Promise<OrderSummary> {
    let params = new HttpParams();
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    return firstValueFrom(this.#http.get<OrderSummary>(`${ORDERS_URL}/summary`, { params }));
  }

  /** HU-11 CA2: cancela un pedido `PENDING` con motivo; el backend devuelve el stock a sus lotes (409 si ya no lo está). */
  cancel(id: number, reason: string): Promise<Order> {
    return firstValueFrom(this.#http.post<Order>(`${ORDERS_URL}/${id}/cancel`, { reason }));
  }

  /** HU-14 CA1/CA2: pedidos propios del mesero aún no entregados (`PENDING`, `IN_PREPARATION`, `READY`). */
  mine(): Promise<Order[]> {
    return firstValueFrom(this.#http.get<Order[]>(`${ORDERS_URL}/mine`));
  }

  /** HU-14 CA3: "Recogido y entregado" (`READY → DELIVERED`); el backend marca leídos sus avisos. */
  deliver(id: number): Promise<Order> {
    return firstValueFrom(this.#http.post<Order>(`${ORDERS_URL}/${id}/deliver`, null));
  }

  /** CA4: meseros activos para asignar el pedido (A, C). */
  waiters(): Promise<Waiter[]> {
    return firstValueFrom(this.#http.get<Waiter[]>(WAITERS_URL));
  }
}
