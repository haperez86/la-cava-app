package co.lacava.gestion.orders.api;

/** Cuerpo de {@code POST /orders/{id}/cancel}; la validación del motivo vive en {@code OrderService.cancel}. */
public record CancelOrderRequest(String reason) {
}
