package co.lacava.gestion.kitchen.application;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.lacava.gestion.orders.application.OrderView;
import co.lacava.gestion.orders.application.OrderViews;
import co.lacava.gestion.orders.domain.OrderStatus;
import co.lacava.gestion.orders.infrastructure.CustomerOrderRepository;

/** Tablero de preparación (LCV-HU-12, D40): pedidos por preparar, en preparación y listos. */
@Service
public class KitchenBoardService {

    static final Set<OrderStatus> BOARD = EnumSet.of(OrderStatus.PENDING, OrderStatus.IN_PREPARATION,
            OrderStatus.READY);

    private final CustomerOrderRepository orders;
    private final OrderViews views;

    public KitchenBoardService(CustomerOrderRepository orders, OrderViews views) {
        this.orders = orders;
        this.views = views;
    }

    /** Ordenados por estado (PENDING → IN_PREPARATION → READY) y, dentro de cada uno, del más antiguo al más reciente. */
    @Transactional(readOnly = true)
    public List<OrderView> board() {
        return views.of(orders.findByStatusInOrderByCreatedAtAscIdAsc(BOARD)).stream()
                .sorted(Comparator.comparing(view -> view.status().ordinal()))
                .toList();
    }
}
