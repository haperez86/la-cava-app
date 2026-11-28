package co.lacava.gestion.notifications.application;

import java.time.Instant;

import co.lacava.gestion.notifications.domain.Notification;
import co.lacava.gestion.notifications.domain.NotificationType;

/** Aviso (respuesta de {@code GET /notifications/unread} y datos del evento SSE {@code notification}). */
public record NotificationView(Long id, NotificationType type, Long orderId, int tableNumber, String message,
        Instant createdAt, Instant readAt) {

    static NotificationView of(Notification n) {
        return new NotificationView(n.getId(), n.getType(), n.getOrderId(), n.getTableNumber(), n.getMessage(),
                n.getCreatedAt(), n.getReadAt());
    }
}
