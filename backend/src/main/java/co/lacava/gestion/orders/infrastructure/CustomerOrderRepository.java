package co.lacava.gestion.orders.infrastructure;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.lacava.gestion.orders.domain.CustomerOrder;
import co.lacava.gestion.orders.domain.OrderStatus;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    /** Pedidos de la mesa en los estados indicados, del más antiguo al más reciente. */
    List<CustomerOrder> findByTableIdAndStatusInOrderByCreatedAtAscIdAsc(Long tableId,
            Collection<OrderStatus> statuses);
}
