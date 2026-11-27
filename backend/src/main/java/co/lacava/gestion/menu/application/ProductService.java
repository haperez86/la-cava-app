package co.lacava.gestion.menu.application;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.lacava.gestion.inventory.application.InventoryService;
import co.lacava.gestion.menu.domain.Category;
import co.lacava.gestion.menu.domain.Product;
import co.lacava.gestion.menu.infrastructure.ProductRepository;
import co.lacava.gestion.shared.error.BusinessException;
import co.lacava.gestion.shared.error.FieldError;
import co.lacava.gestion.shared.error.InvalidFieldsException;

/** Productos de la carta (LCV-HU-02). */
@Service
public class ProductService {

    public static final String DUPLICATE_NAME = "Ya existe un producto con ese nombre.";
    public static final String NOT_FOUND = "El producto no existe.";
    public static final String SEASON_UNTIL_REQUIRED = "Indica la fecha \"disponible hasta\" de la temporada.";
    public static final String EXPIRES_ON_REQUIRED = "La fecha de vencimiento es obligatoria.";
    public static final String INVALID_STATE =
            "El estado debe ser ON_MENU, OUT_OF_STOCK, SEASONAL, OUT_OF_SEASON o INACTIVE.";
    public static final String EXPIRES_ON_PAST = "La fecha de vencimiento no puede ser anterior a hoy.";

    /** Campos por los que se puede ordenar el listado; se desempata siempre por id. */
    private static final Set<String> SORTABLE = Set.of("name", "price", "minStock", "createdAt");

    private final ProductRepository products;
    private final CategoryService categories;
    private final InventoryService inventory;
    private final Clock clock;

    public ProductService(ProductRepository products, CategoryService categories, InventoryService inventory,
            Clock clock) {
        this.products = products;
        this.categories = categories;
        this.inventory = inventory;
        this.clock = clock;
    }

    /**
     * Listado paginado con filtros por nombre (contiene, sin distinguir mayúsculas), categoría y estado
     * ({@link ProductStateFilter}); todos se aplican en la consulta.
     */
    @Transactional(readOnly = true)
    public Page<ProductView> search(String q, Long categoryId, String state, Pageable pageable) {
        LocalDate today = inventory.today();
        Specification<Product> spec = (root, query, cb) -> cb.conjunction();
        if (state != null && !state.isBlank()) {
            ProductStateFilter filter = ProductStateFilter.parse(state).orElseThrow(
                    () -> new InvalidFieldsException(List.of(new FieldError("state", INVALID_STATE))));
            spec = spec.and(filter.toSpecification(today));
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\'));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        Page<Product> page = products.findAll(spec, withSafeSort(pageable));
        Map<Long, Integer> available = inventory.availableByProduct(page.map(Product::getId).getContent());
        return page.map(p -> ProductView.of(p, available.getOrDefault(p.getId(), 0), today));
    }

    @Transactional(readOnly = true)
    public ProductView get(long id) {
        return view(find(id));
    }

    @Transactional(readOnly = true)
    public Optional<ProductView> findByName(String name) {
        return products.findByNameIgnoreCase(name.trim()).map(this::view);
    }

    /**
     * Crea un producto y, si se indica cantidad, su lote inicial con el movimiento {@code LOT_IN}, todo en una
     * transacción: si el lote falla, el producto no queda creado.
     */
    @Transactional
    public ProductView create(ProductData data, InitialLot initialLot, long userId) {
        List<FieldError> errors = validate(data);
        boolean withLot = initialLot != null && initialLot.quantity() != null;
        if (withLot) {
            if (initialLot.expiresOn() == null) {
                errors.add(new FieldError("initialLot.expiresOn", EXPIRES_ON_REQUIRED));
            } else if (initialLot.expiresOn().isBefore(inventory.today())) {
                errors.add(new FieldError("initialLot.expiresOn", EXPIRES_ON_PAST));
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException(errors);
        }
        String name = data.name().trim();
        if (products.existsByNameIgnoreCase(name)) {
            throw BusinessException.conflict(DUPLICATE_NAME);
        }
        Product product = new Product(clock.instant());
        apply(product, data);
        product = products.save(product);
        if (withLot) {
            inventory.receiveLot(product.getId(), initialLot.quantity(), initialLot.expiresOn(), null, userId);
        }
        return view(product);
    }

    @Transactional
    public ProductView update(long id, ProductData data) {
        Product product = find(id);
        List<FieldError> errors = validate(data);
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException(errors);
        }
        if (products.existsByNameIgnoreCaseAndIdNot(data.name().trim(), id)) {
            throw BusinessException.conflict(DUPLICATE_NAME);
        }
        apply(product, data);
        return view(product);
    }

    /** Retira el producto de la carta o lo vuelve a poner (CA3). */
    @Transactional
    public ProductView setActive(long id, boolean active) {
        Product product = find(id);
        product.setActive(active, clock.instant());
        return view(product);
    }

    private Product find(long id) {
        return products.findById(id).orElseThrow(() -> BusinessException.notFound(NOT_FOUND));
    }

    private ProductView view(Product product) {
        return ProductView.of(product, inventory.available(product.getId()), inventory.today());
    }

    /** Reglas entre campos que Bean Validation no expresa (CA2). */
    private static List<FieldError> validate(ProductData data) {
        List<FieldError> errors = new ArrayList<>();
        if (trimToNull(data.seasonName()) != null && data.seasonUntil() == null) {
            errors.add(new FieldError("seasonUntil", SEASON_UNTIL_REQUIRED));
        }
        return errors;
    }

    private void apply(Product product, ProductData data) {
        Category category = categories.findOrCreate(data.categoryName());
        String seasonName = trimToNull(data.seasonName());
        product.update(data.name().trim(), category, data.price().setScale(2, RoundingMode.UNNECESSARY), trimToNull(data.description()),
                trimToNull(data.icon()), data.minStock() == null ? 0 : data.minStock(), seasonName,
                seasonName == null ? null : data.seasonUntil(), clock.instant());
    }

    private static Pageable withSafeSort(Pageable pageable) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new InvalidFieldsException(List.of(new FieldError("sort",
                        "Solo se puede ordenar por: name, price, minStock o createdAt.")));
            }
            orders.add(order);
        }
        if (orders.isEmpty()) {
            orders.add(Sort.Order.asc("name"));
        }
        orders.add(Sort.Order.asc("id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
