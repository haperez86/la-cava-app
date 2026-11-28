import { Component, input } from '@angular/core';
import { CopCurrencyPipe } from '../../shared/pipes/cop-currency.pipe';
import { ORDER_STATUS_LABEL, ORDER_STATUS_PILL } from '../orders/order.model';
import { TableAccount } from './tables.model';

/**
 * Pedidos abiertos de la cuenta de una mesa con sus ítems, observaciones y el total (.order-box de la demo).
 * Presentacional: lo usan el modal de la mesa del mesero (HU-08/HU-10) y la cuenta del cajero (HU-09).
 */
@Component({
  selector: 'app-table-account-orders',
  imports: [CopCurrencyPipe],
  templateUrl: './table-account-orders.html',
  styleUrl: './table-account-orders.scss',
})
export class TableAccountOrders {
  readonly account = input.required<TableAccount>();
  /** Texto de la línea del total. */
  readonly totalLabel = input('Total de la mesa');

  protected readonly statusLabel = ORDER_STATUS_LABEL;
  protected readonly statusPill = ORDER_STATUS_PILL;
}
