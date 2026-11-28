package co.lacava.gestion.notifications.application;

/** Evento de dominio: se guardó un aviso para {@code userId}; se envía por SSE solo después del commit (D42). */
public record NotificationCreated(long userId, NotificationView notification) {
}
