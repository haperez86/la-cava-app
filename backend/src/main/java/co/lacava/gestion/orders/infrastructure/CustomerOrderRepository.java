package co.lacava.gestion.orders.infrastructure;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.lacava.gestion.orders.domain.CustomerOrder;
import co.lacava.gestion.orders.domain.OrderStatus;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    /** Pedidos de la mesa en los estados indicados, del más antiguo al más reciente. */
    List<CustomerOrder> findByTableIdAndStatusInOrderByCreatedAtAscIdAsc(Long tableId,
            Collection<OrderStatus> statuses);

    /**
     * Una fila por mesa con pedidos en {@code statuses} (LCV-HU-09): cantidad, total (suma de
     * {@code unit_price × quantity}), rango de la etapa más urgente (1 = READY, 2 = IN_PREPARATION, 3 = PENDING,
     * 4 = cualquier otro, es decir DELIVERED) y nombre del mesero del pedido más reciente.
     */
    @Query(nativeQuery = true, value = """
            WITH open_order AS (
                SELECT o.id, o.table_id, o.waiter_id, o.status, o.created_at,
                       (SELECT COALESCE(SUM(i.unit_price * i.quantity), 0)
                          FROM order_item i WHERE i.order_id = o.id) AS total
                  FROM customer_order o
                 WHERE o.status IN (:statuses)
            ), summary AS (
                SELECT table_id,
                       COUNT(*) AS open_orders,
                       SUM(total) AS total,
                       MIN(CASE status WHEN 'READY' THEN 1 WHEN 'IN_PREPARATION' THEN 2 WHEN 'PENDING' THEN 3
                           ELSE 4 END) AS stage_rank,
                       (ARRAY_AGG(waiter_id ORDER BY created_at DESC, id DESC))[1] AS last_waiter_id
                  FROM open_order
                 GROUP BY table_id
            )
            SELECT s.table_id AS "tableId", s.open_orders AS "openOrders", s.total AS "total",
                   s.stage_rank AS "stageRank", u.full_name AS "waiterName"
              FROM summary s
              JOIN app_user u ON u.id = s.last_waiter_id
            """)
    List<OpenTableSummary> summarizeOpenByTable(@Param("statuses") Collection<String> statuses);

    /** Proyección de {@link #summarizeOpenByTable}. */
    interface OpenTableSummary {
        Long getTableId();

        Long getOpenOrders();

        BigDecimal getTotal();

        Integer getStageRank();

        String getWaiterName();
    }
}
