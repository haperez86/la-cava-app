package co.lacava.gestion.orders.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.lacava.gestion.orders.domain.CustomerOrder;
import co.lacava.gestion.orders.domain.OrderStatus;
import co.lacava.gestion.orders.infrastructure.CustomerOrderRepository;
import co.lacava.gestion.shared.api.LikePattern;
import co.lacava.gestion.shared.error.FieldError;
import co.lacava.gestion.shared.error.InvalidFieldsException;
import co.lacava.gestion.tables.domain.DiningTable;
import co.lacava.gestion.users.domain.AppUser;
import jakarta.persistence.criteria.Subquery;

/** Listado de pedidos con filtros y contadores por estado (LCV-HU-11, D39). */
@Service
public class OrderQueryService {

    public static final String STATUS_INVALID = "El estado no es válido.";
    public static final String RANGE_INVALID = "La fecha inicial no puede ser posterior a la final.";
    private static final Pattern NUMBER = Pattern.compile("\\d{1,9}");

    private final CustomerOrderRepository orders;
    private final OrderViews views;
    private final ZoneId businessZone;

    public OrderQueryService(CustomerOrderRepository orders, OrderViews views, ZoneId businessZone) {
        this.orders = orders;
        this.views = views;
        this.businessZone = businessZone;
    }

    /**
     * Pedidos del más reciente al más antiguo. {@code q} numérico: número de pedido o de mesa; texto: nombre del
     * mesero (contiene, sin distinguir mayúsculas). {@code from}/{@code to} inclusivas en hora de Bogotá.
     */
    @Transactional(readOnly = true)
    public Page<OrderView> search(String status, String q, LocalDate from, LocalDate to, Pageable pageable) {
        Specification<CustomerOrder> spec = filters(q, from, to);
        if (status != null && !status.isBlank()) {
            OrderStatus parsed = parseStatus(status);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), parsed));
        }
        Page<CustomerOrder> page = orders.findAll(spec, PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new PageImpl<>(views.of(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    /** Contadores por estado con los mismos {@code q}/{@code from}/{@code to}, ignorando el estado. */
    @Transactional(readOnly = true)
    public OrderSummary summary(String q, LocalDate from, LocalDate to) {
        Specification<CustomerOrder> spec = filters(q, from, to);
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        long all = 0;
        for (OrderStatus status : OrderStatus.values()) {
            long n = orders.count(spec.and((root, query, cb) -> cb.equal(root.get("status"), status)));
            counts.put(status, n);
            all += n;
        }
        return new OrderSummary(all, counts.get(OrderStatus.PENDING), counts.get(OrderStatus.IN_PREPARATION),
                counts.get(OrderStatus.READY), counts.get(OrderStatus.DELIVERED), counts.get(OrderStatus.CLOSED),
                counts.get(OrderStatus.CANCELLED));
    }

    private Specification<CustomerOrder> filters(String q, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidFieldsException(List.of(new FieldError("from", RANGE_INVALID)));
        }
        Specification<CustomerOrder> spec = (root, query, cb) -> cb.conjunction();
        if (q != null && !q.isBlank()) {
            String text = q.trim();
            if (NUMBER.matcher(text).matches()) {
                int number = Integer.parseInt(text);
                spec = spec.and((root, query, cb) -> {
                    Subquery<Long> tableIds = query.subquery(Long.class);
                    var table = tableIds.from(DiningTable.class);
                    tableIds.select(table.<Long>get("id")).where(cb.equal(table.get("number"), number));
                    return cb.or(cb.equal(root.get("id"), (long) number), root.get("tableId").in(tableIds));
                });
            } else {
                String pattern = LikePattern.contains(text);
                spec = spec.and((root, query, cb) -> {
                    Subquery<Long> waiterIds = query.subquery(Long.class);
                    var waiter = waiterIds.from(AppUser.class);
                    waiterIds.select(waiter.<Long>get("id"))
                            .where(cb.like(cb.lower(waiter.get("fullName")), pattern, '\\'));
                    return root.get("waiterId").in(waiterIds);
                });
            }
        }
        if (from != null) {
            Instant start = from.atStartOfDay(businessZone).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (to != null) {
            Instant end = to.plusDays(1).atStartOfDay(businessZone).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), end));
        }
        return spec;
    }

    private static OrderStatus parseStatus(String status) {
        try {
            return OrderStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidFieldsException(List.of(new FieldError("status", STATUS_INVALID)));
        }
    }
}
