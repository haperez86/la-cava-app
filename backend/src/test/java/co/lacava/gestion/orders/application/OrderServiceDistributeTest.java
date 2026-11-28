package co.lacava.gestion.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import co.lacava.gestion.inventory.application.LotAllocation;
import co.lacava.gestion.orders.domain.OrderItem;
import co.lacava.gestion.orders.domain.OrderItemLot;

/** CA5: reparto de las asignaciones FEFO de un producto entre sus ítems para {@code order_item_lot}. */
class OrderServiceDistributeTest {

    private static final LocalDate D = LocalDate.parse("2026-03-10");

    @Test
    @DisplayName("CA5 reparte los lotes en orden FEFO entre los ítems del producto en el orden del pedido")
    void distributesAcrossItemsAndLots() {
        List<OrderItemLot> rows = OrderService.distribute(
                List.of(item(10L, 2), item(11L, 3), item(12L, 4)),
                List.of(new LotAllocation(100L, D, 3), new LotAllocation(101L, D, 1), new LotAllocation(102L, D, 5)));

        assertThat(rows).extracting(OrderItemLot::getOrderItemId, OrderItemLot::getLotId, OrderItemLot::getQuantity)
                .containsExactly(
                        tuple(10L, 100L, 2),
                        tuple(11L, 100L, 1), tuple(11L, 101L, 1), tuple(11L, 102L, 1),
                        tuple(12L, 102L, 4));
    }

    @Test
    @DisplayName("CA5 un solo ítem y un solo lote dan una sola fila con toda la cantidad")
    void singleItemSingleLot() {
        assertThat(OrderService.distribute(List.of(item(1L, 5)), List.of(new LotAllocation(9L, D, 5))))
                .extracting(OrderItemLot::getOrderItemId, OrderItemLot::getLotId, OrderItemLot::getQuantity)
                .containsExactly(tuple(1L, 9L, 5));
    }

    private static OrderItem item(long id, int quantity) {
        OrderItem item = new OrderItem(1L, 5L, "Copa", BigDecimal.TEN, quantity, null);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
