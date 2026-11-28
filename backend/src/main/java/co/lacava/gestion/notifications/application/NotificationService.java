package co.lacava.gestion.notifications.application;

import java.time.Clock;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.lacava.gestion.notifications.domain.Notification;
import co.lacava.gestion.notifications.domain.NotificationType;
import co.lacava.gestion.notifications.infrastructure.NotificationRepository;
import co.lacava.gestion.shared.error.BusinessException;
import co.lacava.gestion.users.domain.Role;
import co.lacava.gestion.users.infrastructure.AppUserRepository;

/** Avisos persistidos y su envío por SSE después del commit (D42, CLAUDE.md). */
@Service
public class NotificationService {

    public static final String NOT_FOUND = "El aviso no existe.";

    private final NotificationRepository notifications;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final AppUserRepository users;

    public NotificationService(NotificationRepository notifications, Clock clock, ApplicationEventPublisher events,
            AppUserRepository users) {
        this.users = users;
        this.notifications = notifications;
        this.clock = clock;
        this.events = events;
    }

    /** Guarda el aviso "Pedido #N listo · Mesa M" para el mesero y lo publica; participa de la transacción actual. */
    @Transactional
    public void notifyOrderReady(long waiterId, long orderId, int tableNumber) {
        Notification saved = notifications.save(new Notification(waiterId, NotificationType.ORDER_READY, orderId,
                tableNumber, "Pedido #" + orderId + " listo · Mesa " + tableNumber, clock.instant()));
        events.publishEvent(new NotificationCreated(waiterId, NotificationView.of(saved)));
    }

    /** Guarda "Nuevo pedido #N · Mesa M" para cada usuario activo de Preparación y lo publica (D43); misma transacción. */
    @Transactional
    public void notifyOrderCreated(long orderId, int tableNumber) {
        String message = "Nuevo pedido #" + orderId + " · Mesa " + tableNumber;
        users.findByRoleAndActiveTrueOrderByFullNameAscIdAsc(Role.KITCHEN).forEach(user -> {
            Notification saved = notifications.save(new Notification(user.getId(), NotificationType.ORDER_CREATED,
                    orderId, tableNumber, message, clock.instant()));
            events.publishEvent(new NotificationCreated(user.getId(), NotificationView.of(saved)));
        });
    }

    /** Marca leídos los avisos pendientes del pedido (entrega, CA3). */
    @Transactional
    public void markOrderRead(long orderId) {
        notifications.markOrderRead(orderId, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<NotificationView> unread(long userId) {
        return notifications.findByUserIdAndReadAtIsNullOrderByCreatedAtAscIdAsc(userId).stream()
                .map(NotificationView::of).toList();
    }

    /** Solo avisos propios: uno ajeno o inexistente → 404; marcar uno ya leído no falla. */
    @Transactional
    public void markRead(long id, long userId) {
        Notification notification = notifications.findById(id).filter(n -> n.getUserId() == userId)
                .orElseThrow(() -> BusinessException.notFound(NOT_FOUND));
        notification.markRead(clock.instant());
    }
}
