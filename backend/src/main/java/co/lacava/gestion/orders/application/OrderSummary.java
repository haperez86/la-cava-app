package co.lacava.gestion.orders.application;

/** Contadores de pedidos por estado para las pills del listado (D39); ignoran el filtro de estado. */
public record OrderSummary(long all, long PENDING, long IN_PREPARATION, long READY, long DELIVERED, long CLOSED,
        long CANCELLED) {
}
