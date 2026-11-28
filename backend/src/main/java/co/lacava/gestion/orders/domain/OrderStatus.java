package co.lacava.gestion.orders.domain;

import java.util.EnumSet;
import java.util.Set;

/** Estados del pedido (especificación §3.2). */
public enum OrderStatus {
    PENDING("Pendiente"),
    IN_PREPARATION("En preparación"),
    READY("Listo"),
    DELIVERED("Entregado"),
    CLOSED("Cerrado"),
    CANCELLED("Cancelado");

    /** Estados que forman la cuenta abierta de la mesa (los que se cobran). */
    public static final Set<OrderStatus> OPEN = EnumSet.of(PENDING, IN_PREPARATION, READY, DELIVERED);

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    /** Nombre en español para mensajes al usuario. */
    public String label() {
        return label;
    }
}
