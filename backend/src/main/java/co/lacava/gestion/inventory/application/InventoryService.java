package co.lacava.gestion.inventory.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
import co.lacava.gestion.shared.error.FieldError;
import co.lacava.gestion.shared.error.InvalidFieldsException;

/**
 * Único punto que crea, consume o da de baja lotes; cada cambio registra su movimiento en la misma transacción.
 * LCV-HU-02/04: recibe lotes, los lista con su estado y calcula la existencia vigente; la Épica 2 añade FEFO,
 * salidas y bajas.
 */
@Service
public class InventoryService {

    public static final String QUANTITY_POSITIVE = "La cantidad debe ser mayor que 0.";
    public static final String EXPIRES_ON_REQUIRED = "La fecha de vencimiento es obligatoria.";
    public static final String EXPIRES_ON_PAST = "La fecha de vencimiento no puede ser anterior a hoy.";
    public static final String PRODUCT_NOT_FOUND = "El producto no existe.";

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
     * indicada (nula o en blanco), el lote toma la del producto. Cantidad {@code > 0} y vencimiento {@code >= hoy};
     * si no, 400 con {@code errors[]} por campo. Producto inexistente: 404.
     */
    @Transactional
    public LotView receiveLot(long productId, Integer quantity, LocalDate expiresOn, String seasonName, long userId) {
        List<FieldError> errors = new ArrayList<>();
        if (quantity == null || quantity <= 0) {
            errors.add(new FieldError("quantity", QUANTITY_POSITIVE));
        }
        if (expiresOn == null) {
            errors.add(new FieldError("expiresOn", EXPIRES_ON_REQUIRED));
        } else if (expiresOn.isBefore(today())) {
            errors.add(new FieldError("expiresOn", EXPIRES_ON_PAST));
        }
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException(errors);
        }
        Product product = findProduct(productId);
        StockLot lot = register(product, quantity, expiresOn, seasonName, userId, clock.instant());
        return LotView.of(lot, product.getName(), today());
    }

    /** Lotes de un producto con su estado, ordenados por vencimiento y luego por id. Producto inexistente: 404. */
    @Transactional(readOnly = true)
    public List<LotView> lotsOf(long productId) {
        Product product = findProduct(productId);
        LocalDate today = today();
        return lots.findByProductIdOrderByExpiresOnAscIdAsc(productId).stream()
                .map(lot -> LotView.of(lot, product.getName(), today))
                .toList();
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
        if (quantity <= 0) {
            throw BusinessException.rule(QUANTITY_POSITIVE);
        }
        return register(findProduct(productId), quantity, expiresOn, seasonName, userId, receivedAt);
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

    private Product findProduct(long productId) {
        return products.findById(productId).orElseThrow(() -> BusinessException.notFound(PRODUCT_NOT_FOUND));
    }

    private StockLot register(Product product, int quantity, LocalDate expiresOn, String seasonName, long userId,
            Instant receivedAt) {
        long productId = product.getId();
        String season = seasonName != null && !seasonName.isBlank() ? seasonName.trim() : product.getSeasonName();
        StockLot lot = lots.save(new StockLot(productId, quantity, expiresOn, season, receivedAt, userId));
        movements.save(new StockMovement(productId, lot.getId(), MovementType.LOT_IN, quantity, null, null, userId,
                clock.instant()));
        return lot;
    }
}
