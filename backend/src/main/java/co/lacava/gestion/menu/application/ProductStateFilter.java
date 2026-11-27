package co.lacava.gestion.menu.application;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.jpa.domain.Specification;

import co.lacava.gestion.inventory.application.StockSpecifications;
import co.lacava.gestion.menu.domain.Product;

/** Valores del filtro {@code state} de {@code GET /products} (LCV-HU-03 CA2). */
public enum ProductStateFilter {

    /** Pedibles: activos, en temporada y con existencia vigente. */
    ON_MENU,
    /** Activos, en temporada y sin existencia vigente. */
    OUT_OF_STOCK,
    /** Activos con temporada vigente (con o sin existencia). */
    SEASONAL,
    /** Activos con la temporada vencida. */
    OUT_OF_SEASON,
    /** Retirados de la carta. */
    INACTIVE;

    public static Optional<ProductStateFilter> parse(String value) {
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Condición de la consulta (así la paginación y {@code totalElements} salen correctos). */
    public Specification<Product> toSpecification(LocalDate today) {
        Specification<Product> active = (root, query, cb) -> cb.isTrue(root.get("active"));
        Specification<Product> inSeason = (root, query, cb) -> cb.or(cb.isNull(root.get("seasonUntil")),
                cb.greaterThanOrEqualTo(root.get("seasonUntil"), today));
        Specification<Product> withStock = StockSpecifications.withStock(today);
        return switch (this) {
            case ON_MENU -> active.and(inSeason).and(withStock);
            case OUT_OF_STOCK -> active.and(inSeason).and(Specification.not(withStock));
            case SEASONAL -> active.and((root, query, cb) -> cb.and(cb.isNotNull(root.get("seasonUntil")),
                    cb.greaterThanOrEqualTo(root.get("seasonUntil"), today)));
            case OUT_OF_SEASON -> active.and((root, query, cb) -> cb.lessThan(root.get("seasonUntil"), today));
            case INACTIVE -> (root, query, cb) -> cb.isFalse(root.get("active"));
        };
    }
}
