package co.lacava.gestion.inventory.infrastructure;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.lacava.gestion.inventory.domain.StockLot;
import jakarta.persistence.LockModeType;

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

    /**
     * Lotes vigentes con cantidad de un producto, en orden FEFO (vencimiento y luego id), bloqueados con
     * {@code SELECT … FOR UPDATE} hasta el fin de la transacción (D33).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from StockLot l
            where l.productId = :productId and l.expiresOn >= :today and l.quantityAvailable > 0
            order by l.expiresOn asc, l.id asc
            """)
    List<StockLot> lockAvailable(@Param("productId") Long productId, @Param("today") LocalDate today);

    /** Lotes vencidos ({@code expiresOn < today}) con cantidad, de todos los productos, bloqueados para la baja. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from StockLot l
            where l.expiresOn < :today and l.quantityAvailable > 0
            order by l.productId asc, l.expiresOn asc, l.id asc
            """)
    List<StockLot> lockExpired(@Param("today") LocalDate today);

    boolean existsByProductId(Long productId);

    List<StockLot> findByProductIdOrderByExpiresOnAscIdAsc(Long productId);

    interface ProductAvailability {

        Long getProductId();

        Long getAvailable();
    }
}
