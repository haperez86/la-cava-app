package co.lacava.gestion.notifications.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.lacava.gestion.orders.application.OrderChanged;

/** Envía {@code order-changed} a todos los conectados solo después del commit de quien lo originó (CA3, D41). */
@Component
class OrderChangedBroadcaster {

    static final String EVENT = "order-changed";

    private final SseHub hub;

    OrderChangedBroadcaster(SseHub hub) {
        this.hub = hub;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(OrderChanged event) {
        hub.broadcast(EVENT, event);
    }
}
