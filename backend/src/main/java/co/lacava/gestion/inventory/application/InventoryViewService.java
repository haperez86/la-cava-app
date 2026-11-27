package co.lacava.gestion.inventory.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.lacava.gestion.inventory.domain.StockMovement;
import co.lacava.gestion.inventory.infrastructure.StockLotRepository;
import co.lacava.gestion.inventory.infrastructure.StockLotRepository.ProductStock;
import co.lacava.gestion.inventory.infrastructure.StockMovementRepository;
import co.lacava.gestion.menu.domain.Product;
import co.lacava.gestion.menu.infrastructure.ProductRepository;
import co.lacava.gestion.shared.api.LikePattern;
import co.lacava.gestion.shared.error.FieldError;
import co.lacava.gestion.shared.error.InvalidFieldsException;
import co.lacava.gestion.users.domain.AppUser;
import co.lacava.gestion.users.infrastructure.AppUserRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;

/**
 * Vista de inventario y alertas (LCV-HU-06, D34). Solo lectura: filtra y pagina en la consulta y agrega los lotes
 * de la página con una sola consulta agrupada (sin N+1).
 */
@Service
public class InventoryViewService {

    public static final String SORT_INVALID = "Solo se puede ordenar por: name, available o minStock.";
    public static final String RANGE_INVALID = "La fecha inicial no puede ser posterior a la final.";

    private static final List<String> SORTABLE = List.of("name", "available", "minStock");

    private final ProductRepository products;
    private final StockLotRepository lots;
    private final StockMovementRepository movements;
    private final AppUserRepository users;
    private final InventoryService inventory;
    private final ZoneId businessZone;

    public InventoryViewService(ProductRepository products, StockLotRepository lots,
            StockMovementRepository movements, AppUserRepository users, InventoryService inventory,
            ZoneId businessZone) {
        this.products = products;
        this.lots = lots;
        this.movements = movements;
        this.users = users;
        this.inventory = inventory;
        this.businessZone = businessZone;
    }

    /**
     * Productos activos con su existencia (CA2) y filtros por nombre, categoría, estado y temporada (CA3).
     * Orden por defecto: nombre; se admite {@code name}, {@code available} y {@code minStock}; desempate por id.
     */
    @Transactional(readOnly = true)
    public Page<InventoryRow> search(String q, Long categoryId, String state, String season, Pageable pageable) {
        LocalDate today = inventory.today();
        Specification<Product> spec = active();
        if (state != null && !state.isBlank()) {
            InventoryStateFilter filter = InventoryStateFilter.parse(state).orElseThrow(
                    () -> new InvalidFieldsException(List.of(new FieldError("state", InventoryStateFilter.INVALID))));
            spec = spec.and(filter.toSpecification(today));
        }
        if (q != null && !q.isBlank()) {
            String pattern = LikePattern.contains(q);
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\'));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        if (season != null && !season.isBlank()) {
            String name = season.trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("seasonName")), name));
        }
        spec = spec.and(orderBy(pageable.getSort(), today));
        Page<Product> page = products.findAll(spec,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        Map<Long, ProductStock> stock = page.isEmpty() ? Map.of()
                : lots.stockByProduct(page.map(Product::getId).getContent(), today).stream()
                        .collect(Collectors.toMap(ProductStock::getProductId, Function.identity()));
        return page.map(p -> row(p, stock.get(p.getId()), today));
    }

    /** Contadores de alertas sobre los productos activos, con las mismas definiciones que los filtros (CA1). */
    @Transactional(readOnly = true)
    public InventorySummary summary() {
        LocalDate today = inventory.today();
        return new InventorySummary(
                products.count(active().and(InventoryStateFilter.OUT.toSpecification(today))),
                products.count(active().and(InventoryStateFilter.LOW.toSpecification(today))),
                products.count(active().and(InventoryStateFilter.EXPIRING.toSpecification(today))),
                lots.sumExpiredOfActiveProducts(today),
                products.findActiveSeasonNames());
    }

    /**
     * Movimientos recientes (CA4), del más nuevo al más viejo, con filtro por producto y por fechas
     * {@code from}/{@code to} inclusivas en la zona de negocio. {@code from > to}: 400 en {@code from}.
     */
    @Transactional(readOnly = true)
    public Page<MovementView> movements(Long productId, LocalDate from, LocalDate to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidFieldsException(List.of(new FieldError("from", RANGE_INVALID)));
        }
        Specification<StockMovement> spec = (root, query, cb) -> cb.conjunction();
        if (productId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("productId"), productId));
        }
        if (from != null) {
            Instant start = from.atStartOfDay(businessZone).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (to != null) {
            Instant end = to.plusDays(1).atStartOfDay(businessZone).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), end));
        }
        Page<StockMovement> page = movements.findAll(spec, PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        Map<Long, String> productNames = products
                .findAllById(page.map(StockMovement::getProductId).toSet()).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));
        Map<Long, String> userNames = users.findAllById(page.map(StockMovement::getUserId).toSet()).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));
        return page.map(m -> new MovementView(m.getId(), m.getCreatedAt(), m.getProductId(),
                productNames.get(m.getProductId()), m.getLotId(), m.getType(), m.getQuantity(), m.getNote(),
                m.getOrderId(), userNames.get(m.getUserId())));
    }

    private static InventoryRow row(Product product, ProductStock stock, LocalDate today) {
        int available = stock == null ? 0 : Math.toIntExact(stock.getAvailable());
        int expiredUnits = stock == null ? 0 : Math.toIntExact(stock.getExpiredUnits());
        LocalDate next = stock == null ? null : stock.getNextExpiresOn();
        Integer days = next == null ? null : Math.toIntExact(ChronoUnit.DAYS.between(today, next));
        InventoryState state = InventoryState.of(product.getSeasonUntil(), available, product.getMinStock(), days,
                today);
        return new InventoryRow(product.getId(), product.getName(), product.getIcon(), product.getCategory().getId(),
                product.getCategory().getName(), product.getSeasonName(), product.getSeasonUntil(), available,
                product.getMinStock(), next, days, expiredUnits, state, expiredUnits > 0);
    }

    private static Specification<Product> active() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    /**
     * Orden de la consulta dentro de la especificación, porque {@code available} es una subconsulta y no una
     * propiedad. Spring Data descarta el orden en la consulta de conteo.
     */
    private static Specification<Product> orderBy(Sort sort, LocalDate today) {
        for (Sort.Order order : sort) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new InvalidFieldsException(List.of(new FieldError("sort", SORT_INVALID)));
            }
        }
        return (root, query, cb) -> {
            List<Order> orders = new ArrayList<>();
            for (Sort.Order order : sort) {
                Expression<?> expression = switch (order.getProperty()) {
                    case "available" -> StockSpecifications.available(root, query, cb, today);
                    case "minStock" -> root.get("minStock");
                    default -> root.get("name");
                };
                orders.add(order.isAscending() ? cb.asc(expression) : cb.desc(expression));
            }
            if (orders.isEmpty()) {
                orders.add(cb.asc(root.get("name")));
            }
            orders.add(cb.asc(root.get("id")));
            query.orderBy(orders);
            return cb.conjunction();
        };
    }
}
