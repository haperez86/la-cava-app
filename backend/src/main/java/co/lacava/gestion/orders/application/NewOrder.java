package co.lacava.gestion.orders.application;

import java.util.List;

/**
 * Datos de un pedido nuevo tal como llegan ({@code POST /orders}); el servicio los valida. {@code tableCode} es el
 * comprobante de mesa (D37) y no se expone en {@code toString}.
 */
public record NewOrder(Long tableId, Long waiterId, List<Item> items, String note, String tableCode) {

    public record Item(Long productId, Integer quantity, String note) {
    }

    @Override
    public String toString() {
        return "NewOrder[tableId=" + tableId + ", waiterId=" + waiterId + ", items=" + items + "]";
    }
}
