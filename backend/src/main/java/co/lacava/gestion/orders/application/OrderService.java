package co.lacava.gestion.orders.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.lacava.gestion.inventory.application.InsufficientStockException;
import co.lacava.gestion.inventory.application.InventoryService;
import co.lacava.gestion.inventory.application.LotAllocation;
import co.lacava.gestion.inventory.domain.MovementType;
import co.lacava.gestion.menu.application.ProductState;
import co.lacava.gestion.notifications.application.NotificationService;
import co.lacava.gestion.menu.domain.Product;
import co.lacava.gestion.menu.infrastructure.ProductRepository;
import co.lacava.gestion.orders.domain.CustomerOrder;
import co.lacava.gestion.orders.domain.OrderItem;
import co.lacava.gestion.orders.domain.OrderItemLot;
import co.lacava.gestion.orders.domain.OrderStateMachine;
import co.lacava.gestion.orders.domain.OrderStatus;
import co.lacava.gestion.orders.infrastructure.CustomerOrderRepository;
import co.lacava.gestion.orders.infrastructure.OrderItemLotRepository;
import co.lacava.gestion.orders.infrastructure.OrderItemRepository;
import co.lacava.gestion.shared.error.BusinessException;
import co.lacava.gestion.shared.error.FieldError;
import co.lacava.gestion.shared.error.InvalidFieldsException;
import co.lacava.gestion.tables.application.TablePassService;
import co.lacava.gestion.tables.domain.DiningTable;
import co.lacava.gestion.tables.infrastructure.DiningTableRepository;
import co.lacava.gestion.users.domain.AppUser;
import co.lacava.gestion.users.domain.Role;
import co.lacava.gestion.users.infrastructure.AppUserRepository;

/**
 * Pedidos (LCV-HU-10, D37, D38). Al crear: valida, consume los lotes en FEFO con {@link InventoryService#consume}
 * recorriendo los productos en orden ascendente de id (D33), guarda {@code order_item_lot} y deja el pedido en
 * {@code PENDING}, todo en una transacción. Producto no disponible o existencia insuficiente → 422 y no se guarda nada.
 */
@Service
public class OrderService {

    public static final int ITEM_NOTE_MAX = 80;
    /** Tope por ítem: evita desbordes al sumar cantidades del mismo producto y cantidades absurdas. */
    public static final int QUANTITY_MAX = 999;
    public static final int NOTE_MAX = 140;
    public static final String TABLE_REQUIRED = "La mesa es obligatoria.";
    public static final String ITEMS_REQUIRED = "Agrega al menos un producto.";
    public static final String PRODUCT_REQUIRED = "El producto es obligatorio.";
    public static final String QUANTITY_POSITIVE = "La cantidad debe ser un entero mayor que 0.";
    public static final String QUANTITY_TOO_LARGE = "La cantidad es demasiado grande (máximo " + QUANTITY_MAX
            + " por producto).";
    public static final String ITEM_NOTE_TOO_LONG = "La observación del producto admite máximo " + ITEM_NOTE_MAX
            + " caracteres.";
    public static final String NOTE_TOO_LONG = "La observación del pedido admite máximo " + NOTE_MAX + " caracteres.";
    public static final String WAITER_REQUIRED = "Selecciona el mesero.";
    public static final String WAITER_INVALID = "El mesero no existe o no está activo.";
    public static final String SCAN_AGAIN = "Escanea de nuevo el QR de la mesa.";
    public static final String TABLE_NOT_FOUND = "La mesa no existe.";
    public static final String ORDER_NOT_FOUND = "El pedido no existe.";
    public static final String PRODUCT_NOT_FOUND = "El producto no existe.";
    public static final int CANCEL_REASON_MAX = 120;
    public static final String CANCEL_REASON_REQUIRED = "El motivo de cancelación es obligatorio.";
    public static final String CANCEL_REASON_TOO_LONG = "El motivo admite máximo " + CANCEL_REASON_MAX
            + " caracteres.";
    public static final String ONLY_READY_NOTIFIABLE = "Solo se puede reenviar el aviso de un pedido listo.";
    private static final List<OrderStatus> MINE_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.IN_PREPARATION,
            OrderStatus.READY);
    public static final String ONLY_PENDING_CANCELLABLE = "Solo se puede cancelar un pedido pendiente.";

    private final CustomerOrderRepository orders;
    private final OrderItemRepository items;
    private final OrderItemLotRepository itemLots;
    private final DiningTableRepository tables;
    private final AppUserRepository users;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final TablePassService passes;
    private final OrderViews views;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final NotificationService notifications;

    public OrderService(CustomerOrderRepository orders, OrderItemRepository items, OrderItemLotRepository itemLots,
            DiningTableRepository tables, AppUserRepository users, ProductRepository products,
            InventoryService inventory, TablePassService passes, OrderViews views, Clock clock,
            ApplicationEventPublisher events, NotificationService notifications) {
        this.events = events;
        this.notifications = notifications;
        this.orders = orders;
        this.items = items;
        this.itemLots = itemLots;
        this.tables = tables;
        this.users = users;
        this.products = products;
        this.inventory = inventory;
        this.passes = passes;
        this.views = views;
        this.clock = clock;
    }

    /**
     * Crea un pedido en {@code PENDING} (CA2–CA5).
     * <ul>
     * <li>Mesero: queda asignado (se ignora {@code waiterId}) y {@code tableCode} debe ser un comprobante de mesa
     * válido de esa mesa, emitido para él y vigente (D37); si no, 403.</li>
     * <li>Cajero/Administrador: {@code waiterId} obligatorio y de un mesero activo (400); la mesa debe existir (404) y
     * estar activa (422).</li>
     * </ul>
     *
     * @param actorId usuario que registra el pedido ({@code created_by} y usuario de los movimientos)
     * @param actorRole su rol
     */
    @Transactional
    public OrderView create(NewOrder request, long actorId, Role actorRole) {
        Normalized data = validate(request, actorRole);
        DiningTable table;
        long waiterId;
        if (actorRole == Role.WAITER) {
            table = tables.findById(data.tableId)
                    .filter(t -> passes.verify(request.tableCode(), t, actorId))
                    .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, SCAN_AGAIN));
            waiterId = actorId;
        } else if (actorRole == Role.ADMIN || actorRole == Role.CASHIER) {
            waiterId = activeWaiter(request.waiterId()).getId();
            table = tables.findById(data.tableId).orElseThrow(() -> BusinessException.notFound(TABLE_NOT_FOUND));
            if (!table.isActive()) {
                throw BusinessException.rule("La mesa " + table.getNumber() + " está inactiva.");
            }
        } else {
            throw new AccessDeniedException("Rol sin permiso para crear pedidos: " + actorRole);
        }

        // Cantidad pedida por producto, en orden ascendente de productId (D33).
        TreeMap<Long, Integer> wanted = new TreeMap<>();
        for (Line line : data.lines) {
            wanted.merge(line.productId, line.quantity, Math::addExact);
        }
        Map<Long, Product> productById = products.findAllById(wanted.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (Long productId : wanted.keySet()) {
            if (!productById.containsKey(productId)) {
                throw BusinessException.notFound(PRODUCT_NOT_FOUND);
            }
        }
        checkAvailability(wanted, productById);

        CustomerOrder order = orders.save(new CustomerOrder(table.getId(), waiterId, actorId, data.note,
                clock.instant()));
        List<OrderItem> saved = new ArrayList<>(data.lines.size());
        for (Line line : data.lines) {
            Product product = productById.get(line.productId);
            saved.add(items.save(new OrderItem(order.getId(), product.getId(), product.getName(), product.getPrice(),
                    line.quantity, line.note)));
        }

        for (Map.Entry<Long, Integer> entry : wanted.entrySet()) {
            Product product = productById.get(entry.getKey());
            List<LotAllocation> allocations;
            try {
                allocations = inventory.consume(product.getId(), entry.getValue(), MovementType.SALE, null, actorId,
                        order.getId());
            } catch (InsufficientStockException e) {
                // Otra mesa consumió antes (o el vigente cambió tras la verificación): se revierte todo.
                throw notEnough(product, e.getAvailable());
            }
            List<OrderItem> ofProduct = saved.stream().filter(i -> i.getProductId().equals(product.getId())).toList();
            itemLots.saveAll(distribute(ofProduct, allocations));
        }
        notifications.notifyOrderCreated(order.getId(), table.getNumber());
        events.publishEvent(changed(order));
        return views.of(order);
    }

    /**
     * Cancela un pedido {@code PENDING} con motivo (LCV-HU-11, D39). Motivo recortado de 1 a 120 caracteres (400 en
     * {@code reason}); inexistente → 404; fuera de {@code PENDING} → 409. La transición solo pasa por
     * {@link OrderStateMachine} y la devolución, por {@link InventoryService}, a los lotes exactos de
     * {@code order_item_lot} (un movimiento por lote), todo en una transacción.
     */
    @Transactional
    public OrderView cancel(long id, String reason, long actorId) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidFieldsException(List.of(new FieldError("reason", CANCEL_REASON_REQUIRED)));
        }
        if (trimmed.length() > CANCEL_REASON_MAX) {
            throw new InvalidFieldsException(List.of(new FieldError("reason", CANCEL_REASON_TOO_LONG)));
        }
        CustomerOrder order = orders.findById(id).orElseThrow(() -> BusinessException.notFound(ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw BusinessException.conflict(ONLY_PENDING_CANCELLABLE);
        }
        OrderStateMachine.transition(order, OrderStatus.CANCELLED, clock.instant(), trimmed);
        // Si otra petición cambió el pedido (@Version), falla aquí y no se devuelve nada.
        orders.saveAndFlush(order);
        Map<Long, Integer> byLot = new TreeMap<>();
        for (OrderItemLot used : itemLots.findByOrderId(order.getId())) {
            byLot.merge(used.getLotId(), used.getQuantity(), Math::addExact);
        }
        inventory.returnToLots(order.getId(), byLot, actorId);
        events.publishEvent(changed(order));
        return views.of(order);
    }

    /** Preparación inicia el pedido: {@code PENDING → IN_PREPARATION} con {@code started_at} (LCV-HU-12, D40). */
    @Transactional
    public OrderView start(long id) {
        return advance(id, OrderStatus.IN_PREPARATION);
    }

    /**
     * Preparación lo marca listo: {@code IN_PREPARATION → READY} con {@code ready_at}. En la misma transacción crea el
     * aviso {@code ORDER_READY} del mesero, que sale por SSE solo a sus conexiones después del commit (HU-14, D42).
     */
    @Transactional
    public OrderView ready(long id) {
        OrderView view = advance(id, OrderStatus.READY);
        notifications.notifyOrderReady(view.waiterId(), view.id(), view.tableNumber());
        return view;
    }

    /** Preparación reenvía el aviso: solo en {@code READY} (si no, 409); crea un aviso nuevo igual (CA4, D42). */
    @Transactional
    public void notifyAgain(long id) {
        CustomerOrder order = orders.findById(id).orElseThrow(() -> BusinessException.notFound(ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.READY) {
            throw BusinessException.conflict(ONLY_READY_NOTIFIABLE);
        }
        int tableNumber = tables.findById(order.getTableId()).orElseThrow().getNumber();
        notifications.notifyOrderReady(order.getWaiterId(), order.getId(), tableNumber);
    }

    /**
     * El mesero (solo su pedido; ajeno → 404, D38) o el administrador entrega: {@code READY → DELIVERED} con
     * {@code delivered_at}, marca leídos los avisos del pedido y emite {@code order-changed} (CA3, D42).
     */
    @Transactional
    public OrderView deliver(long id, long actorId, Role actorRole) {
        CustomerOrder order = orders.findById(id)
                .filter(o -> actorRole != Role.WAITER || o.getWaiterId() == actorId)
                .orElseThrow(() -> BusinessException.notFound(ORDER_NOT_FOUND));
        OrderView view = advance(order.getId(), OrderStatus.DELIVERED);
        notifications.markOrderRead(order.getId());
        return view;
    }

    /** Pedidos propios del mesero aún no entregados ({@code PENDING}, {@code IN_PREPARATION}, {@code READY}). */
    @Transactional(readOnly = true)
    public List<OrderView> mine(long waiterId) {
        return views.of(orders.findByWaiterIdAndStatusInOrderByCreatedAtAscIdAsc(waiterId, MINE_STATUSES));
    }

    /** Transición solo por {@link OrderStateMachine}: inválida → 409, inexistente → 404, conflicto de versión → 409. */
    private OrderView advance(long id, OrderStatus to) {
        CustomerOrder order = orders.findById(id).orElseThrow(() -> BusinessException.notFound(ORDER_NOT_FOUND));
        OrderStateMachine.transition(order, to, clock.instant(), null);
        orders.saveAndFlush(order);
        events.publishEvent(changed(order));
        return views.of(order);
    }

    /** {@code order-changed} (HU-13): llega a los conectados solo si la transacción se confirma. */
    private static OrderChanged changed(CustomerOrder order) {
        return new OrderChanged(order.getId(), order.getTableId(), order.getStatus());
    }

    /** {@code true} si ya hay algún pedido (idempotencia de los datos de ejemplo). */
    @Transactional(readOnly = true)
    public boolean hasAny() {
        return orders.count() > 0;
    }

    /** Pedido por id. El mesero solo ve los suyos: uno ajeno responde 404, igual que uno inexistente. */
    @Transactional(readOnly = true)
    public OrderView get(long id, long actorId, Role actorRole) {
        CustomerOrder order = orders.findById(id)
                .filter(o -> actorRole != Role.WAITER || o.getWaiterId() == actorId)
                .orElseThrow(() -> BusinessException.notFound(ORDER_NOT_FOUND));
        return views.of(order);
    }

    /**
     * Reparte, en orden FEFO, las asignaciones de lote de un producto entre sus ítems (en el orden del pedido):
     * cada ítem toma lo que le falta del lote en curso y sigue con el siguiente.
     */
    static List<OrderItemLot> distribute(List<OrderItem> itemsOfProduct, List<LotAllocation> allocations) {
        Map<String, OrderItemLot> result = new LinkedHashMap<>();
        int lotIndex = 0;
        int leftInLot = allocations.isEmpty() ? 0 : allocations.getFirst().quantity();
        for (OrderItem item : itemsOfProduct) {
            int pending = item.getQuantity();
            while (pending > 0) {
                while (leftInLot == 0) {
                    lotIndex++;
                    leftInLot = allocations.get(lotIndex).quantity();
                }
                int taken = Math.min(pending, leftInLot);
                Long lotId = allocations.get(lotIndex).lotId();
                result.merge(item.getId() + ":" + lotId, new OrderItemLot(item.getId(), lotId, taken),
                        (a, b) -> new OrderItemLot(a.getOrderItemId(), a.getLotId(), a.getQuantity() + b.getQuantity()));
                pending -= taken;
                leftInLot -= taken;
            }
        }
        return new ArrayList<>(result.values());
    }

    /**
     * CA2: producto retirado o fuera de temporada, o cantidad mayor que la vigente → 422 con el producto y lo
     * disponible. Es una verificación previa sin bloqueo (no carga lotes); la definitiva la hace {@code consume}.
     */
    private void checkAvailability(Map<Long, Integer> wanted, Map<Long, Product> productById) {
        Map<Long, Integer> available = inventory.availableByProduct(wanted.keySet());
        LocalDate today = inventory.today();
        for (Map.Entry<Long, Integer> entry : wanted.entrySet()) {
            Product product = productById.get(entry.getKey());
            int stock = available.getOrDefault(product.getId(), 0);
            ProductState state = ProductState.of(product.isActive(), product.getSeasonUntil(), today, stock);
            if (state == ProductState.INACTIVE || state == ProductState.OUT_OF_SEASON) {
                throw unavailable(product, stock, state);
            }
            if (entry.getValue() > stock) {
                throw notEnough(product, stock);
            }
        }
    }

    private AppUser activeWaiter(Long waiterId) {
        return users.findById(waiterId)
                .filter(AppUser::isActive)
                .filter(u -> u.getRole() == Role.WAITER)
                .orElseThrow(() -> new InvalidFieldsException(List.of(new FieldError("waiterId", WAITER_INVALID))));
    }

    /** CA3 y validación de forma: 400 con {@code errors[]} por campo; recorta las observaciones. */
    private static Normalized validate(NewOrder request, Role actorRole) {
        List<FieldError> errors = new ArrayList<>();
        if (request.tableId() == null) {
            errors.add(new FieldError("tableId", TABLE_REQUIRED));
        }
        if (actorRole != Role.WAITER && request.waiterId() == null) {
            errors.add(new FieldError("waiterId", WAITER_REQUIRED));
        }
        List<Line> lines = new ArrayList<>();
        if (request.items() == null || request.items().isEmpty()) {
            errors.add(new FieldError("items", ITEMS_REQUIRED));
        } else {
            for (int i = 0; i < request.items().size(); i++) {
                NewOrder.Item item = request.items().get(i);
                String prefix = "items[" + i + "].";
                if (item == null) {
                    errors.add(new FieldError("items[" + i + "]", PRODUCT_REQUIRED));
                    continue;
                }
                if (item.productId() == null) {
                    errors.add(new FieldError(prefix + "productId", PRODUCT_REQUIRED));
                }
                if (item.quantity() == null || item.quantity() <= 0) {
                    errors.add(new FieldError(prefix + "quantity", QUANTITY_POSITIVE));
                } else if (item.quantity() > QUANTITY_MAX) {
                    errors.add(new FieldError(prefix + "quantity", QUANTITY_TOO_LARGE));
                }
                String note = trimToNull(item.note());
                if (note != null && note.length() > ITEM_NOTE_MAX) {
                    errors.add(new FieldError(prefix + "note", ITEM_NOTE_TOO_LONG));
                }
                lines.add(new Line(item.productId(), item.quantity() == null ? 0 : item.quantity(), note));
            }
        }
        String note = trimToNull(request.note());
        if (note != null && note.length() > NOTE_MAX) {
            errors.add(new FieldError("note", NOTE_TOO_LONG));
        }
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException(errors);
        }
        return new Normalized(request.tableId(), lines, note);
    }

    private static BusinessException notEnough(Product product, int available) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT,
                "No hay suficiente " + product.getName() + ": disponibles " + available + ".",
                problemProperties(product, available, null));
    }

    private static BusinessException unavailable(Product product, int available, ProductState state) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT,
                product.getName() + " no está disponible: " + state.blockReason() + ".",
                problemProperties(product, available, state.blockReason()));
    }

    private static Map<String, Object> problemProperties(Product product, int available, String reason) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("productId", product.getId());
        properties.put("productName", product.getName());
        properties.put("available", available);
        if (reason != null) {
            properties.put("reason", reason);
        }
        return properties;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record Line(Long productId, int quantity, String note) {
    }

    private record Normalized(Long tableId, List<Line> lines, String note) {
    }
}
