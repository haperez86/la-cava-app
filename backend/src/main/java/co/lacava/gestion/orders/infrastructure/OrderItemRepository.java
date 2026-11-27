package co.lacava.gestion.orders.infrastructure;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.lacava.gestion.orders.domain.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);
}
