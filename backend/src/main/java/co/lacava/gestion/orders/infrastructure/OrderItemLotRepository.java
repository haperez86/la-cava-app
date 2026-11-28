package co.lacava.gestion.orders.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.lacava.gestion.orders.domain.OrderItemLot;

public interface OrderItemLotRepository extends JpaRepository<OrderItemLot, OrderItemLot.Key> {

    /** Lo que consumió cada ítem de un pedido, por lote (para devolverlo al cancelar, D11). */
    @Query("""
            select l from OrderItemLot l
            where l.id.orderItemId in (select i.id from OrderItem i where i.orderId = :orderId)
            """)
    List<OrderItemLot> findByOrderId(@Param("orderId") Long orderId);
}
