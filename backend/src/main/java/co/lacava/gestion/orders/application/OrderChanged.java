package co.lacava.gestion.orders.application;

import co.lacava.gestion.orders.domain.OrderStatus;

/**
 * Evento de dominio: un pedido se creó o cambió de estado (HU-13, D41). Se publica dentro de la transacción y quien lo
 * escucha con {@code AFTER_COMMIT} solo lo ve si esta se confirma. El JSON es el de {@code order-changed} en SSE.
 */
public record OrderChanged(Long orderId, Long tableId, OrderStatus status) {
}
