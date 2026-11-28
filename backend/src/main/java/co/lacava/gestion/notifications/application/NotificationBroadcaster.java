package co.lacava.gestion.notifications.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Envía el evento SSE {@code notification} solo a las conexiones del destinatario, después del commit (D42). */
@Component
class NotificationBroadcaster {

    static final String EVENT = "notification";

    private final SseHub hub;

    NotificationBroadcaster(SseHub hub) {
        this.hub = hub;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(NotificationCreated event) {
        hub.sendTo(event.userId(), EVENT, event.notification());
    }
}
