package co.lacava.gestion.orders.api;

import java.util.List;

import co.lacava.gestion.orders.application.NewOrder;

/**
 * Cuerpo de {@code POST /orders}. La validación (con {@code errors[]} por índice de ítem) la hace el servicio.
 * {@code tableCode} es el comprobante de mesa ({@code tablePass} de {@code resolve}, D37) y no sale en
 * {@code toString}.
 */
record CreateOrderRequest(Long tableId, Long waiterId, List<ItemRequest> items, String note, String tableCode) {

    record ItemRequest(Long productId, Integer quantity, String note) {
    }

    NewOrder toNewOrder() {
        List<NewOrder.Item> lines = items == null ? null
                : items.stream()
                        .map(i -> i == null ? null : new NewOrder.Item(i.productId(), i.quantity(), i.note()))
                        .toList();
        return new NewOrder(tableId, waiterId, lines, note, tableCode);
    }

    @Override
    public String toString() {
        return "CreateOrderRequest[tableId=" + tableId + ", waiterId=" + waiterId + "]";
    }
}
