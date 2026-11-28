import { escapeHtml, TICKET_CSS } from '../../core/print/ticket';
import { formatBusinessDateTime } from '../../shared/dates/business-date';
import { Order } from '../orders/order.model';

/** CA3: comanda de preparación en tirilla de 80 mm (idea de `printOrder` de la demo): mesa, mesero, hora, ítems y observaciones. */
export function comandaHtml(order: Order): string {
  const lines = order.items
    .map(
      (item) =>
        `<tr><td><b>${escapeHtml(item.quantity)}</b></td><td>${escapeHtml(item.productName)}${
          item.note ? `<br><span class="sm">» ${escapeHtml(item.note)}</span>` : ''
        }</td></tr>`,
    )
    .join('');
  const note = order.note ? `<hr><p>Obs.: ${escapeHtml(order.note)}</p>` : '';
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Comanda #${escapeHtml(order.id)}</title><style>${TICKET_CSS}</style></head><body><h1>LA CAVA</h1><p class="c"><b>COMANDA DE PREPARACIÓN</b></p><hr><p>Pedido: #${escapeHtml(order.id)}<br>Mesa: <b>${escapeHtml(order.tableNumber)}</b><br>Mesero: ${escapeHtml(order.waiterName)}<br>Hora: ${escapeHtml(formatBusinessDateTime(order.createdAt))}</p><table><thead><tr><th>Cant</th><th>Producto</th></tr></thead><tbody>${lines}</tbody></table>${note}</body></html>`;
}
