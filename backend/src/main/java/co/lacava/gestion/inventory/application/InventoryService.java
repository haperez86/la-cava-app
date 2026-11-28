package co.lacava.gestion.inventory.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * LCV-HU-02/04: recibe lotes, los lista con su estado y calcula la existencia vigente. LCV-HU-05: consumo FEFO
 * con bloqueo pesimista, salidas manuales y baja de vencidos (D33).
 */
@Service
public class InventoryService {

    public static final String QUANTITY_POSITIVE = "La cantidad debe ser mayor que 0.";
    public static final String EXPIRES_ON_REQUIRED = "La fecha de vencimiento es obligatoria.";
    public static final String EXPIRES_ON_PAST = "La fecha de vencimiento no puede ser anterior a hoy.";
    public static final String PRODUCT_NOT_FOUND = "El producto no existe.";
    /** {@code stock_movement.note} es {@code varchar(140)} (V3). */
    public static final int NOTE_MAX = 140;
    public static final String OUTPUT_TYPE_INVALID =
            "El tipo de salida no es válido (WASTE_OUT, DAMAGED_OUT, INTERNAL_USE_OUT o ADJUSTMENT_OUT).";
    public static final String NOTE_REQUIRED = "La observación es obligatoria.";
    public static final String NOTE_TOO_LONG = "La observación admite máximo " + NOTE_MAX + " caracteres.";

    /** Tipos admitidos en una salida manual (D33). */
    public static final Set<MovementType> OUTPUT_TYPES = EnumSet.of(MovementType.WASTE_OUT,
            MovementType.DAMAGED_OUT, MovementType.INTERNAL_USE_OUT, MovementType.ADJUSTMENT_OUT);

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

    /**
     * Consumo FEFO (D33): bloquea con {@code SELECT … FOR UPDATE} los lotes vigentes del producto en orden de
     * vencimiento y descuenta {@code quantity} repartiéndola entre ellos. Registra un movimiento de cantidad negativa
     * por lote tocado (con {@code orderId} si se indica), en la transacción del llamador si la hay. Cantidad {@code > 0}
     * y {@code <=} la existencia vigente; si no, {@link InsufficientStockException} (400 en {@code quantity}) y no
     * cambia nada.
     * <p>
     * Contrato para el llamador:
     * <ul>
     * <li>Si consume varios productos en una misma transacción, debe llamarlo en orden ascendente de
     * {@code productId} (el mismo orden de bloqueo que {@code lockExpired}) para evitar interbloqueos.</li>
     * <li>No debe cargar esas entidades {@code StockLot} antes en la misma transacción: el contexto de persistencia
     * conservaría su estado viejo en lugar del que devuelve el bloqueo y el {@code @Version} fallaría con
     * {@code OptimisticLockException}.</li>
     * </ul>
     *
     * @param type {@code SALE} o uno de {@link #OUTPUT_TYPES}; cualquier otro es un error de programación
     * @return cuánto tomó de cada lote, en orden FEFO
     * @throws IllegalArgumentException si {@code type} no es un tipo de consumo
     */
    @Transactional
    public List<LotAllocation> consume(long productId, Integer quantity, MovementType type, String note, long userId,
            Long orderId) {
        if (type != MovementType.SALE && !OUTPUT_TYPES.contains(type)) {
            throw new IllegalArgumentException("Tipo de movimiento no admitido en un consumo: " + type);
        }
        if (quantity == null || quantity <= 0) {
            throw new InvalidFieldsException(List.of(new FieldError("quantity", QUANTITY_POSITIVE)));
        }
        List<StockLot> available = lots.lockAvailable(productId, today());
        int total = available.stream().mapToInt(StockLot::getQuantityAvailable).sum();
        if (total < quantity) {
            throw new InsufficientStockException(productId, total);
        }
        Instant now = clock.instant();
        List<LotAllocation> allocations = new ArrayList<>();
        int pending = quantity;
        for (StockLot lot : available) {
            if (pending == 0) {
                break;
            }
            int taken = lot.take(pending);
            pending -= taken;
            allocations.add(new LotAllocation(lot.getId(), lot.getExpiresOn(), taken));
            movements.save(new StockMovement(productId, lot.getId(), type, -taken, note, orderId, userId, now));
        }
        return allocations;
    }

    /**
     * Devuelve al stock lo que consumió un pedido cancelado (LCV-HU-11, D39): suma cada cantidad al lote exacto de
     * {@code order_item_lot} (también si ya venció: queda vencida, spec §3.2) y registra un movimiento
     * {@code ORDER_CANCEL_RETURN} positivo por lote, en la transacción del llamador.
     *
     * @param quantityByLot cantidad total a devolver por id de lote
     */
    @Transactional
    public void returnToLots(long orderId, Map<Long, Integer> quantityByLot, long userId) {
        if (quantityByLot.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        for (StockLot lot : lots.lockByIds(quantityByLot.keySet())) {
            int units = quantityByLot.get(lot.getId());
            lot.giveBack(units);
            movements.save(new StockMovement(lot.getProductId(), lot.getId(), MovementType.ORDER_CANCEL_RETURN, units,
                    null, orderId, userId, now));
        }
    }

    /**
     * Salida manual (LCV-HU-05 CA2): {@code type} en {@link #OUTPUT_TYPES}, observación obligatoria (recortada,
     * máximo {@link #NOTE_MAX}) y cantidad {@code > 0} y {@code <=} la vigente; si no, 400 por campo.
     * Producto inexistente: 404. Consume en FEFO con {@link #consume}.
     */
    @Transactional
    public OutputView registerOutput(Long productId, Integer quantity, String type, String note, long userId) {
        List<FieldError> errors = new ArrayList<>();
        if (productId == null) {
            errors.add(new FieldError("productId", "El producto es obligatorio."));
        }
        if (quantity == null || quantity <= 0) {
            errors.add(new FieldError("quantity", QUANTITY_POSITIVE));
        }
        MovementType movementType = outputType(type);
        if (movementType == null) {
            errors.add(new FieldError("type", OUTPUT_TYPE_INVALID));
        }
        String trimmedNote = note == null ? "" : note.trim();
        if (trimmedNote.isEmpty()) {
            errors.add(new FieldError("note", NOTE_REQUIRED));
        } else if (trimmedNote.length() > NOTE_MAX) {
            errors.add(new FieldError("note", NOTE_TOO_LONG));
        }
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException(errors);
        }
        Product product = findProduct(productId);
        List<LotAllocation> consumed = consume(productId, quantity, movementType, trimmedNote, userId, null);
        return new OutputView(productId, product.getName(), movementType, quantity, consumed, available(productId));
    }

    /**
     * "Dar de baja vencidos" (LCV-HU-05 CA3): deja en 0 los lotes con {@code expiresOn < hoy} y cantidad, de todos los
     * productos, con un movimiento {@code EXPIRED_WRITE_OFF} de cantidad negativa por lote, en una transacción.
     */
    @Transactional
    public WriteOffView writeOffExpired(long userId) {
        Instant now = clock.instant();
        int lotCount = 0;
        int units = 0;
        for (StockLot lot : lots.lockExpired(today())) {
            int written = lot.writeOff();
            movements.save(new StockMovement(lot.getProductId(), lot.getId(), MovementType.EXPIRED_WRITE_OFF,
                    -written, null, null, userId, now));
            lotCount++;
            units += written;
        }
        return new WriteOffView(lotCount, units);
    }

    /** Mensaje de cantidad mayor que la existencia vigente. */
    public static String notEnough(int available) {
        return "Solo hay " + available + " disponibles.";
    }

    private static MovementType outputType(String type) {
        if (type == null) {
            return null;
        }
        return OUTPUT_TYPES.stream().filter(t -> t.name().equals(type.trim())).findFirst().orElse(null);
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
