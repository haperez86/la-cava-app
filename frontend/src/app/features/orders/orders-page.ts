import { Component, signal } from '@angular/core';
import { Order } from './order.model';
import { OrderDialog } from './order-dialog';

export const ORDERS_EMPTY = 'Los pedidos creados aparecerán aquí.';

/**
 * `/pedidos` "Pedidos" (ADMIN y CASHIER, D38): "Nuevo pedido" abre el modal con mesa y mesero obligatorios
 * (LCV-HU-10 CA4). El listado de pedidos llega con LCV-HU-11.
 */
@Component({
  selector: 'app-orders-page',
  imports: [OrderDialog],
  templateUrl: './orders-page.html',
  styleUrl: './orders-page.scss',
})
export class OrdersPage {
  protected readonly creating = signal(false);
  protected readonly emptyText = ORDERS_EMPTY;

  protected openCreate(): void {
    this.creating.set(true);
  }

  protected closeCreate(): void {
    this.creating.set(false);
  }

  protected onCreated(_order: Order): void {
    this.creating.set(false);
  }
}
