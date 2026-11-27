package co.lacava.gestion.orders.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import co.lacava.gestion.shared.error.BusinessException;

/**
 * Única vía para cambiar el estado de un pedido (CLAUDE.md, especificación §3.2):
 * <pre>
 * PENDING → IN_PREPARATION → READY → DELIVERED
 * PENDING → CANCELLED (con motivo)
 * PENDING | IN_PREPARATION | READY | DELIVERED → CLOSED (cobrar la cuenta)
 * </pre>
 * Cualquier otra transición → 409. En LCV-HU-10 solo se crean pedidos en {@code PENDING}; el resto de transiciones
 * las usan las Épicas 5 y 6.
 */
public final class OrderStateMachine {

    public static final String CANCEL_REASON_REQUIRED = "El motivo de cancelación es obligatorio.";

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING,
                EnumSet.of(OrderStatus.IN_PREPARATION, OrderStatus.CANCELLED, OrderStatus.CLOSED));
        ALLOWED.put(OrderStatus.IN_PREPARATION, EnumSet.of(OrderStatus.READY, OrderStatus.CLOSED));
        ALLOWED.put(OrderStatus.READY, EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CLOSED));
        ALLOWED.put(OrderStatus.DELIVERED, EnumSet.of(OrderStatus.CLOSED));
        ALLOWED.put(OrderStatus.CLOSED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {
    }

    /** {@code true} si la especificación permite pasar de {@code from} a {@code to}. */
    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return from != null && to != null && ALLOWED.get(from).contains(to);
    }

    /** Mensaje del 409 por transición inválida. */
    public static String invalidTransition(OrderStatus from, OrderStatus to) {
        return "El pedido está " + from.label().toLowerCase() + " y no puede pasar a "
                + to.label().toLowerCase() + ".";
    }

    /**
     * Pasa el pedido a {@code to} y fija la marca de tiempo del estado. Transición inválida → 409; cancelar sin motivo
     * → 422.
     */
    public static void transition(CustomerOrder order, OrderStatus to, Instant now, String cancelReason) {
        OrderStatus from = order.getStatus();
        if (!canTransition(from, to)) {
            throw BusinessException.conflict(invalidTransition(from, to));
        }
        if (to == OrderStatus.CANCELLED && (cancelReason == null || cancelReason.isBlank())) {
            throw BusinessException.rule(CANCEL_REASON_REQUIRED);
        }
        order.applyStatus(to, now, to == OrderStatus.CANCELLED ? cancelReason.trim() : null);
    }
}
