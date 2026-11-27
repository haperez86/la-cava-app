package co.lacava.gestion.inventory.application;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.jpa.domain.Specification;

import co.lacava.gestion.menu.domain.Product;

/**
 * Valores del filtro {@code state} de {@code GET /inventory} (LCV-HU-06 CA3, D34). Son conjuntos que pueden
 * solaparse; todos se aplican en la consulta para que la paginación y {@code totalElements} salgan correctos.
 */
public enum InventoryStateFilter {

    /** Filas con estado {@link InventoryState#AVAILABLE}. */
    AVAILABLE,
    /** En temporada y {@code 0 < vigente <= mínimo}. */
    LOW,
    /** En temporada y sin existencia vigente. */
    OUT,
    /** Con algún lote vigente que vence en 3 días o menos (esté o no en temporada). */
    EXPIRING,
    /** Con unidades vencidas ({@code expiredUnits > 0}). */
    HAS_EXPIRED,
    /** Temporada vencida. */
    OUT_OF_SEASON;

    public static final String INVALID =
            "El estado debe ser AVAILABLE, LOW, OUT, EXPIRING, HAS_EXPIRED u OUT_OF_SEASON.";

    public static Optional<InventoryStateFilter> parse(String value) {
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Condición sobre los productos (no incluye "activo": la vista ya lo exige). */
    public Specification<Product> toSpecification(LocalDate today) {
        Specification<Product> inSeason = (root, query, cb) -> cb.or(cb.isNull(root.get("seasonUntil")),
                cb.greaterThanOrEqualTo(root.get("seasonUntil"), today));
        Specification<Product> withStock = StockSpecifications.withStock(today);
        Specification<Product> lowOrLess = (root, query, cb) -> cb.le(
                StockSpecifications.available(root, query, cb, today), root.<Integer>get("minStock"));
        Specification<Product> expiring = StockSpecifications.expiringSoon(today);
        return switch (this) {
            case OUT -> inSeason.and(Specification.not(withStock));
            case LOW -> inSeason.and(withStock).and(lowOrLess);
            case AVAILABLE -> inSeason.and(withStock).and(Specification.not(lowOrLess))
                    .and(Specification.not(expiring));
            case EXPIRING -> expiring;
            case HAS_EXPIRED -> StockSpecifications.withExpired(today);
            case OUT_OF_SEASON -> (root, query, cb) -> cb.lessThan(root.get("seasonUntil"), today);
        };
    }
}
