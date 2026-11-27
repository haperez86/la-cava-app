package co.lacava.gestion.inventory.application;

import java.time.LocalDate;
import java.util.function.Function;

import org.springframework.data.jpa.domain.Specification;

import co.lacava.gestion.inventory.domain.StockLot;
import co.lacava.gestion.menu.domain.Product;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Condiciones de consulta sobre la existencia (solo lectura; los lotes los modifica {@code InventoryService}).
 * Todas son subconsultas correlacionadas sobre {@code stock_lot}, para filtrar y paginar en la misma consulta.
 */
public final class StockSpecifications {

    private StockSpecifications() {
    }

    /** Productos con existencia vigente: algún lote con cantidad {@code > 0} y vencimiento {@code >= today}. */
    public static Specification<Product> withStock(LocalDate today) {
        return (root, query, cb) -> lotExists(root, query, cb,
                lot -> cb.greaterThanOrEqualTo(lot.<LocalDate>get("expiresOn"), today));
    }

    /** Algún lote vigente con cantidad que vence en {@link LotStatus#EXPIRING_DAYS} días o menos (D34). */
    public static Specification<Product> expiringSoon(LocalDate today) {
        return (root, query, cb) -> lotExists(root, query, cb,
                lot -> cb.between(lot.<LocalDate>get("expiresOn"), today, today.plusDays(LotStatus.EXPIRING_DAYS)));
    }

    /** Algún lote vencido ({@code expiresOn < today}) que aún tiene cantidad. */
    public static Specification<Product> withExpired(LocalDate today) {
        return (root, query, cb) -> lotExists(root, query, cb,
                lot -> cb.lessThan(lot.<LocalDate>get("expiresOn"), today));
    }

    /** Existencia vigente del producto como expresión ({@code 0} si no tiene lotes vigentes). */
    public static Expression<Integer> available(Root<Product> root, AbstractQuery<?> query, CriteriaBuilder cb,
            LocalDate today) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<StockLot> lot = sub.from(StockLot.class);
        sub.select(cb.coalesce(cb.sum(lot.<Integer>get("quantityAvailable")), 0)).where(
                cb.equal(lot.get("productId"), root.get("id")),
                cb.greaterThan(lot.<Integer>get("quantityAvailable"), 0),
                cb.greaterThanOrEqualTo(lot.<LocalDate>get("expiresOn"), today));
        return sub;
    }

    private static Predicate lotExists(Root<Product> root, AbstractQuery<?> query, CriteriaBuilder cb,
            Function<Root<StockLot>, Predicate> condition) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<StockLot> lot = sub.from(StockLot.class);
        sub.select(cb.literal(1)).where(
                cb.equal(lot.get("productId"), root.get("id")),
                cb.greaterThan(lot.<Integer>get("quantityAvailable"), 0),
                condition.apply(lot));
        return cb.exists(sub);
    }
}
