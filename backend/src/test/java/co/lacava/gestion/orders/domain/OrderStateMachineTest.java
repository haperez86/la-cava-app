package co.lacava.gestion.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import co.lacava.gestion.shared.error.BusinessException;

/** Máquina de estados del pedido (especificación §3.2). */
class OrderStateMachineTest {

    private static final Instant T0 = Instant.parse("2026-03-10T04:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);

    private static final Set<String> ALLOWED = Set.of(
            "PENDING>IN_PREPARATION", "PENDING>CANCELLED", "PENDING>CLOSED",
            "IN_PREPARATION>READY", "IN_PREPARATION>CLOSED",
            "READY>DELIVERED", "READY>CLOSED",
            "DELIVERED>CLOSED");

    @Test
    @DisplayName("Solo se permiten las transiciones de §3.2; todas las demás (incluidas las de un estado a sí mismo) no")
    void transitionTable() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                assertThat(OrderStateMachine.canTransition(from, to))
                        .as(from + " -> " + to)
                        .isEqualTo(ALLOWED.contains(from + ">" + to));
            }
        }
    }

    @Test
    @DisplayName("Un pedido nuevo nace PENDING y recorre IN_PREPARATION, READY, DELIVERED y CLOSED con sus marcas de tiempo")
    void happyPath() {
        CustomerOrder order = new CustomerOrder(1L, 2L, 3L, null, T0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        OrderStateMachine.transition(order, OrderStatus.IN_PREPARATION, T1, null);
        assertThat(order.getStartedAt()).isEqualTo(T1);
        OrderStateMachine.transition(order, OrderStatus.READY, T1.plusSeconds(1), null);
        assertThat(order.getReadyAt()).isEqualTo(T1.plusSeconds(1));
        OrderStateMachine.transition(order, OrderStatus.DELIVERED, T1.plusSeconds(2), null);
        assertThat(order.getDeliveredAt()).isEqualTo(T1.plusSeconds(2));
        OrderStateMachine.transition(order, OrderStatus.CLOSED, T1.plusSeconds(3), null);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(order.getClosedAt()).isEqualTo(T1.plusSeconds(3));
    }

    @Test
    @DisplayName("Cancelar solo desde PENDING y con motivo (recortado); sin motivo da 422 y el estado no cambia")
    void cancel() {
        CustomerOrder order = new CustomerOrder(1L, 2L, 3L, null, T0);
        assertThatThrownBy(() -> OrderStateMachine.transition(order, OrderStatus.CANCELLED, T1, "  "))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        OrderStateMachine.transition(order, OrderStatus.CANCELLED, T1, "  Cliente se fue  ");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isEqualTo(T1);
        assertThat(order.getCancelReason()).isEqualTo("Cliente se fue");
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = { "PENDING", "IN_PREPARATION", "READY", "DELIVERED" })
    @DisplayName("Cobrar la cuenta cierra los pedidos abiertos en cualquiera de sus estados")
    void closeFromOpenStates(OrderStatus open) {
        assertThat(OrderStatus.OPEN).contains(open);
        assertThat(OrderStateMachine.canTransition(open, OrderStatus.CLOSED)).isTrue();
    }

    @Test
    @DisplayName("Una transición inválida da 409 con un mensaje en español y el estado no cambia")
    void invalidTransitionIsConflict() {
        CustomerOrder order = new CustomerOrder(1L, 2L, 3L, null, T0);
        for (OrderStatus to : EnumSet.of(OrderStatus.READY, OrderStatus.DELIVERED, OrderStatus.PENDING)) {
            assertThatThrownBy(() -> OrderStateMachine.transition(order, to, T1, null))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        }
        OrderStateMachine.transition(order, OrderStatus.IN_PREPARATION, T1, null);
        assertThatThrownBy(() -> OrderStateMachine.transition(order, OrderStatus.CANCELLED, T1, "Tarde"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El pedido está en preparación y no puede pasar a cancelado.");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PREPARATION);
        assertThat(order.getCancelledAt()).isNull();
    }
}
