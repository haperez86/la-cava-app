package co.lacava.gestion.orders.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.lacava.gestion.orders.domain.CustomerOrder;
import co.lacava.gestion.orders.domain.OrderItem;
import co.lacava.gestion.orders.domain.OrderStatus;
import co.lacava.gestion.orders.infrastructure.CustomerOrderRepository;
import co.lacava.gestion.orders.infrastructure.OrderItemRepository;
import co.lacava.gestion.tables.application.OpenTableOrders;
import co.lacava.gestion.tables.application.TableAccount;
import co.lacava.gestion.tables.application.TableOccupancy;
import co.lacava.gestion.tables.application.TableStage;
import co.lacava.gestion.tables.domain.DiningTable;
import co.lacava.gestion.tables.infrastructure.DiningTableRepository;
import co.lacava.gestion.users.domain.AppUser;
import co.lacava.gestion.users.infrastructure.AppUserRepository;

/**
 * Arma {@link OrderView} en lote (ítems, mesas y meseros con una consulta cada uno) y da a {@code tables} la cuenta
 * abierta de una mesa (D38) y su resumen de ocupación (LCV-HU-09). Solo usa repositorios: así {@code TableService}
 * puede depender de él sin ciclo.
 */
@Component
public class OrderViews implements OpenTableOrders {

    /** Rango 1..4 de {@code summarizeOpenByTable} → etapa (§3.3: READY > IN_PREPARATION > PENDING > SERVED). */
    private static final TableStage[] STAGE_BY_RANK = { TableStage.READY, TableStage.IN_PREPARATION,
            TableStage.PENDING, TableStage.SERVED };

    private final CustomerOrderRepository orders;
    private final OrderItemRepository items;
    private final DiningTableRepository tables;
    private final AppUserRepository users;

    public OrderViews(CustomerOrderRepository orders, OrderItemRepository items, DiningTableRepository tables,
            AppUserRepository users) {
        this.orders = orders;
        this.items = items;
        this.tables = tables;
        this.users = users;
    }

    /** Pedidos {@code PENDING}, {@code IN_PREPARATION}, {@code READY} y {@code DELIVERED} de la mesa, y su total. */
    @Override
    @Transactional(readOnly = true)
    public TableAccount.Account openAccount(long tableId) {
        List<OrderView> open = of(orders.findByTableIdAndStatusInOrderByCreatedAtAscIdAsc(tableId, OrderStatus.OPEN));
        BigDecimal total = open.stream().map(OrderView::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TableAccount.Account(open, total);
    }

    /** LCV-HU-09: una sola consulta agregada (GROUP BY mesa) para todas las mesas ocupadas. */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, TableOccupancy> occupancyByTable() {
        List<String> open = OrderStatus.OPEN.stream().map(OrderStatus::name).toList();
        return orders.summarizeOpenByTable(open).stream()
                .map(s -> new TableOccupancy(s.getTableId(), s.getOpenOrders().intValue(), s.getTotal(),
                        STAGE_BY_RANK[s.getStageRank() - 1], s.getWaiterName()))
                .collect(Collectors.toMap(TableOccupancy::tableId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public OrderView of(CustomerOrder order) {
        return of(List.of(order)).getFirst();
    }

    /** Vistas en el mismo orden que {@code source}. */
    @Transactional(readOnly = true)
    public List<OrderView> of(Collection<CustomerOrder> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        Set<Long> orderIds = source.stream().map(CustomerOrder::getId).collect(Collectors.toSet());
        Map<Long, List<OrderItem>> itemsByOrder = items.findByOrderIdInOrderByIdAsc(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        Map<Long, DiningTable> tableById = tables
                .findAllById(source.stream().map(CustomerOrder::getTableId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(DiningTable::getId, Function.identity()));
        Map<Long, AppUser> waiterById = users
                .findAllById(source.stream().map(CustomerOrder::getWaiterId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        List<OrderView> views = new ArrayList<>(source.size());
        for (CustomerOrder order : source) {
            List<OrderItemView> lines = itemsByOrder.getOrDefault(order.getId(), List.of()).stream()
                    .map(OrderItemView::of).toList();
            BigDecimal total = lines.stream().map(OrderItemView::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            DiningTable table = tableById.get(order.getTableId());
            AppUser waiter = waiterById.get(order.getWaiterId());
            views.add(new OrderView(order.getId(), order.getTableId(), table.getNumber(), order.getWaiterId(),
                    waiter.getFullName(), order.getStatus(), order.getNote(), order.getCreatedAt(), lines, total,
                    order.getCancelReason(), order.getCancelledAt()));
        }
        return views;
    }
}
