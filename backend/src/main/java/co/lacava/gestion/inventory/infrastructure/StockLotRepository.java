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

    /** Lotes por id (vigentes o no), bloqueados en el mismo orden que el consumo (producto, vencimiento, id). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from StockLot l
            where l.id in :ids
            order by l.productId asc, l.expiresOn asc, l.id asc
            """)
    List<StockLot> lockByIds(@Param("ids") Collection<Long> ids);

    /** Lotes vencidos ({@code expiresOn < today}) con cantidad, de todos los productos, bloqueados para la baja. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from StockLot l
            where l.expiresOn < :today and l.quantityAvailable > 0
            order by l.productId asc, l.expiresOn asc, l.id asc
            """)
    List<StockLot> lockExpired(@Param("today") LocalDate today);

    /**
     * Agregados por producto para la vista de inventario (D34), en una consulta: existencia vigente, vencimiento del
     * próximo lote vigente con cantidad y unidades vencidas ({@code expiresOn < today}).
     */
    @Query("""
            select l.productId as productId,
                   sum(case when l.expiresOn >= :today then l.quantityAvailable else 0 end) as available,
                   min(case when l.expiresOn >= :today then l.expiresOn end) as nextExpiresOn,
                   sum(case when l.expiresOn < :today then l.quantityAvailable else 0 end) as expiredUnits
            from StockLot l
            where l.productId in :productIds and l.quantityAvailable > 0
            group by l.productId
            """)
    List<ProductStock> stockByProduct(@Param("productIds") Collection<Long> productIds,
            @Param("today") LocalDate today);

    /** Unidades vencidas ({@code expiresOn < today}) que aún tienen los lotes de productos activos. */
    @Query("""
            select coalesce(sum(l.quantityAvailable), 0)
            from StockLot l join Product p on p.id = l.productId
            where p.active = true and l.expiresOn < :today and l.quantityAvailable > 0
            """)
    long sumExpiredOfActiveProducts(@Param("today") LocalDate today);

    boolean existsByProductId(Long productId);

    List<StockLot> findByProductIdOrderByExpiresOnAscIdAsc(Long productId);

    interface ProductStock {

        Long getProductId();

        Long getAvailable();

        LocalDate getNextExpiresOn();

        Long getExpiredUnits();
    }

    interface ProductAvailability {

        Long getProductId();

        Long getAvailable();
    }
}
