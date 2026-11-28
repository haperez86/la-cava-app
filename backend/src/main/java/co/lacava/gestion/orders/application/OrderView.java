package co.lacava.gestion.orders.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import co.lacava.gestion.orders.domain.OrderStatus;

/** Pedido con mesa, mesero, ítems y total (respuesta de {@code POST /orders}, {@code GET /orders/{id}} y la cuenta); startedAt y readyAt desde HU-12 (D40). */
public record OrderView(Long id, Long tableId, int tableNumber, Long waiterId, String waiterName, OrderStatus status,
        String note, Instant createdAt, List<OrderItemView> items, BigDecimal total, String cancelReason,
        Instant cancelledAt, Instant startedAt, Instant readyAt) {
}
