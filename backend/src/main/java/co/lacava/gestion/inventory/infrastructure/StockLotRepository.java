package co.lacava.gestion.inventory.infrastructure;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.lacava.gestion.inventory.domain.StockLot;

public interface StockLotRepository extends JpaRepository<StockLot, Long> {

    /** Existencia vigente por producto: suma de lotes con vencimiento {@code >= today} y cantidad disponible. */
    @Query("""
            select l.productId as productId, sum(l.quantityAvailable) as available
            from StockLot l
            where l.productId in :productIds and l.expiresOn >= :today and l.quantityAvailable > 0
            group by l.productId
            """)
    List<ProductAvailability> sumAvailable(@Param("productIds") Collection<Long> productIds,
            @Param("today") LocalDate today);

    boolean existsByProductId(Long productId);

    interface ProductAvailability {

        Long getProductId();

        Long getAvailable();
    }
}
