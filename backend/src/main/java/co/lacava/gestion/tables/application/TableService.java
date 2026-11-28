package co.lacava.gestion.tables.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.lacava.gestion.shared.error.BusinessException;
import co.lacava.gestion.shared.security.AttemptLimiter;
import co.lacava.gestion.tables.domain.DiningTable;
import co.lacava.gestion.tables.infrastructure.DiningTableRepository;

/** Mesas y sus códigos QR (LCV-HU-07, D35). */
@Service
public class TableService {

    public static final String DUPLICATE_NUMBER = "Ya existe una mesa con ese número.";
    public static final String NOT_FOUND = "La mesa no existe.";
    /** Mismo mensaje para código mal formado, token que no coincide, PIN errado y mesa inexistente o inactiva. */
    public static final String INVALID_CODE = "Código de mesa inválido";

    private final DiningTableRepository tables;
    private final TableTokenService tokens;
    private final Clock clock;
    private final PinAttemptService pinAttempts;
    private final TablePassService passes;
    private final OpenTableOrders openOrders;

    public TableService(DiningTableRepository tables, TableTokenService tokens, Clock clock,
            PinAttemptService pinAttempts, TablePassService passes, OpenTableOrders openOrders) {
        this.tables = tables;
        this.tokens = tokens;
        this.clock = clock;
        this.pinAttempts = pinAttempts;
        this.passes = passes;
        this.openOrders = openOrders;
    }

    /** LCV-HU-09 CA1: mesas por número con estado, etapa, pedidos abiertos, total y último mesero (sin N+1). */
    @Transactional(readOnly = true)
    public List<TableView> list() {
        Map<Long, TableOccupancy> occupancy = openOrders.occupancyByTable();
        return tables.findAllByOrderByNumberAsc().stream()
                .map(t -> TableView.of(t.getId(), t.getNumber(), t.isActive(), occupancy.get(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean existsByNumber(int number) {
        return tables.existsByNumber(number);
    }

    /** CA1: alta con token HMAC y PIN de 4 dígitos. */
    @Transactional
    public TableView create(int number) {
        if (tables.existsByNumber(number)) {
            throw BusinessException.conflict(DUPLICATE_NUMBER);
        }
        DiningTable table = new DiningTable(number, tokens.newToken(number), tokens.newPin(), clock.instant());
        DiningTable saved = tables.save(table);
        return TableView.of(saved.getId(), saved.getNumber(), saved.isActive(), null);
    }

    /** CA1: activa o desactiva la mesa. */
    @Transactional
    public TableView setActive(long id, boolean active) {
        DiningTable table = find(id);
        table.setActive(active);
        return view(table);
    }

    /** CA3: nuevo token (nuevo nonce) y nuevo PIN; el QR y el PIN anteriores dejan de servir. */
    @Transactional
    public TableView rotateToken(long id) {
        DiningTable table = find(id);
        table.rotate(tokens.newToken(table.getNumber()), tokens.newPin(), clock.instant());
        return view(table);
    }

    /** CA2: datos de la hoja de QR, solo mesas activas, ordenadas por número. */
    @Transactional(readOnly = true)
    public List<QrSheetEntry> qrSheet() {
        return tables.findByActiveTrueOrderByNumberAsc().stream()
                .map(t -> new QrSheetEntry(t.getNumber(), tokens.code(t.getNumber(), t.getQrToken()), t.getPin()))
                .toList();
    }

    /**
     * Mesa activa cuyo código del QR coincide (comparación en tiempo constante), o 404 genérico. Devuelve la cuenta
     * abierta y un comprobante de mesa (D37) emitido para {@code userId}.
     */
    @Transactional(readOnly = true)
    public ResolvedTable resolveByCode(String code, long userId) {
        DiningTable table = tokens.parse(code)
                .flatMap(parsed -> activeByNumber(parsed.number())
                        .filter(t -> tokens.matches(t.getQrToken(), parsed.token())))
                .orElseThrow(TableService::invalidCode);
        return resolved(table, userId);
    }

    /**
     * Respaldo manual: número de mesa y PIN (comparación en tiempo constante), o 404 genérico. Limitado por usuario
     * (D36): el intento se reserva antes de comparar; durante el bloqueo responde el mismo 404 aunque el PIN sea
     * correcto. Siempre se busca y compara, para que el bloqueo no se distinga por el tiempo de respuesta.
     */
    @Transactional(readOnly = true)
    public ResolvedTable resolveByPin(int number, String pin, long userId, String username) {
        AttemptLimiter.Permit permit = pinAttempts.tryAcquire(userId);
        Optional<DiningTable> table = activeByNumber(number).filter(t -> tokens.matches(t.getPin(), pin));
        if (!permit.allowed()) {
            throw invalidCode();
        }
        if (table.isEmpty()) {
            pinAttempts.recordFailure(permit, userId, username, number);
            throw invalidCode();
        }
        pinAttempts.recordSuccess(userId);
        return resolved(table.get(), userId);
    }

    @Transactional(readOnly = true)
    public TableAccount account(long id) {
        return account(find(id));
    }

    private Optional<DiningTable> activeByNumber(int number) {
        return tables.findByNumber(number).filter(DiningTable::isActive);
    }

    private DiningTable find(long id) {
        return tables.findById(id).orElseThrow(() -> BusinessException.notFound(NOT_FOUND));
    }

    private TableAccount account(DiningTable table) {
        return new TableAccount(table.getId(), table.getNumber(), openOrders.openAccount(table.getId()));
    }

    private ResolvedTable resolved(DiningTable table, long userId) {
        TablePassService.TablePass pass = passes.issue(table, userId);
        return new ResolvedTable(table.getId(), table.getNumber(), openOrders.openAccount(table.getId()),
                pass.value(), pass.expiresAt());
    }

    private TableView view(DiningTable table) {
        return TableView.of(table.getId(), table.getNumber(), table.isActive(),
                openOrders.occupancyByTable().get(table.getId()));
    }

    private static BusinessException invalidCode() {
        return BusinessException.notFound(INVALID_CODE);
    }
}
