package co.lacava.gestion.orders.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import co.lacava.gestion.orders.domain.OrderItemLot;

public interface OrderItemLotRepository extends JpaRepository<OrderItemLot, OrderItemLot.Key> {
}
