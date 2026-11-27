package co.lacava.gestion.orders.application;

import java.math.BigDecimal;

import co.lacava.gestion.orders.domain.OrderItem;

/** Ítem de un pedido con su subtotal ({@code unitPrice × quantity}). */
public record OrderItemView(Long id, Long productId, String productName, BigDecimal unitPrice, int quantity,
        String note, BigDecimal subtotal) {

    public static OrderItemView of(OrderItem item) {
        return new OrderItemView(item.getId(), item.getProductId(), item.getProductName(), item.getUnitPrice(),
                item.getQuantity(), item.getNote(), item.subtotal());
    }
}
