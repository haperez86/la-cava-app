package co.lacava.gestion.inventory.application;

import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;

import co.lacava.gestion.inventory.domain.StockLot;
import co.lacava.gestion.menu.domain.Product;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Condiciones de consulta sobre la existencia (solo lectura; los lotes los modifica {@code InventoryService}). */
public final class StockSpecifications {

    private StockSpecifications() {
    }

    /** Productos con existencia vigente: algún lote con cantidad {@code > 0} y vencimiento {@code >= today}. */
    public static Specification<Product> withStock(LocalDate today) {
        return (root, query, cb) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<StockLot> lot = sub.from(StockLot.class);
            sub.select(cb.literal(1)).where(
                    cb.equal(lot.get("productId"), root.get("id")),
                    cb.greaterThan(lot.<Integer>get("quantityAvailable"), 0),
                    cb.greaterThanOrEqualTo(lot.<LocalDate>get("expiresOn"), today));
            return cb.exists(sub);
        };
    }
}
