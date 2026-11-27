package co.lacava.gestion.inventory.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.lacava.gestion.inventory.domain.MovementType;
import co.lacava.gestion.inventory.domain.StockLot;
import co.lacava.gestion.inventory.domain.StockMovement;
import co.lacava.gestion.inventory.infrastructure.StockLotRepository;
import co.lacava.gestion.inventory.infrastructure.StockMovementRepository;
import co.lacava.gestion.menu.domain.Product;
import co.lacava.gestion.menu.infrastructure.ProductRepository;
import co.lacava.gestion.shared.error.BusinessException;

/**
 * Único punto que crea, consume o da de baja lotes; cada cambio registra su movimiento en la misma transacción.
 * En LCV-HU-02 solo recibe lotes y calcula la existencia vigente; la Épica 2 añade FEFO, salidas y bajas.
 */
@Service
public class InventoryService {

    private final StockLotRepository lots;
    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final Clock clock;
    private final ZoneId businessZone;

    public InventoryService(StockLotRepository lots, StockMovementRepository movements, ProductRepository products,
            Clock clock, ZoneId businessZone) {
        this.lots = lots;
        this.movements = movements;
        this.products = products;
        this.clock = clock;
        this.businessZone = businessZone;
    }

    /** "Hoy" en la zona de negocio según el {@code Clock} (D16). */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), businessZone);
    }

    /**
     * Recibe un lote: crea el {@code stock_lot} y su movimiento {@code LOT_IN} en la misma transacción. Sin temporada
     * indicada, el lote toma la del producto. Cantidad {@code > 0} y vencimiento {@code >= hoy}; si no, 422 (la API
     * valida antes y responde 400 por campo).
     */
    @Transactional
    public StockLot receiveLot(long productId, int quantity, LocalDate expiresOn, String seasonName, long userId) {
        if (expiresOn == null || expiresOn.isBefore(today())) {
            throw BusinessException.rule("La fecha de vencimiento es obligatoria y no puede ser anterior a hoy.");
        }
        return register(productId, quantity, expiresOn, seasonName, userId, clock.instant());
    }

    /**
     * Solo para los datos de ejemplo del perfil {@code local}: admite lotes ya vencidos y una fecha de recepción
     * pasada. Igual que {@link #receiveLot}, registra el movimiento {@code LOT_IN}.
     */
    @Transactional
    public StockLot loadSampleLot(long productId, int quantity, LocalDate expiresOn, String seasonName,
            Instant receivedAt, long userId) {
        if (expiresOn == null) {
            throw BusinessException.rule("La fecha de vencimiento es obligatoria.");
        }
        return register(productId, quantity, expiresOn, seasonName, userId, receivedAt);
    }

    /** {@code true} si el producto tiene algún lote (vigente o no). */
    @Transactional(readOnly = true)
    public boolean hasLots(long productId) {
        return lots.existsByProductId(productId);
    }

    /** Existencia vigente de un producto. */
    @Transactional(readOnly = true)
    public int available(long productId) {
        return availableByProduct(List.of(productId)).getOrDefault(productId, 0);
    }

    /** Existencia vigente por producto (los productos sin lotes vigentes no aparecen en el mapa). */
    @Transactional(readOnly = true)
    public Map<Long, Integer> availableByProduct(Collection<Long> productIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        for (StockLotRepository.ProductAvailability row : lots.sumAvailable(productIds, today())) {
            result.put(row.getProductId(), Math.toIntExact(row.getAvailable()));
        }
        return result;
    }

    private StockLot register(long productId, int quantity, LocalDate expiresOn, String seasonName, long userId,
            Instant receivedAt) {
        if (quantity <= 0) {
            throw BusinessException.rule("La cantidad del lote debe ser mayor que 0.");
        }
        Product product = products.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("El producto no existe."));
        String season = seasonName != null && !seasonName.isBlank() ? seasonName.trim() : product.getSeasonName();
        StockLot lot = lots.save(new StockLot(productId, quantity, expiresOn, season, receivedAt, userId));
        movements.save(new StockMovement(productId, lot.getId(), MovementType.LOT_IN, quantity, null, null, userId,
                clock.instant()));
        return lot;
    }
}
