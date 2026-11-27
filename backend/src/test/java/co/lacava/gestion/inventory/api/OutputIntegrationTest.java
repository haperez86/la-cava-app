package co.lacava.gestion.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import co.lacava.gestion.inventory.application.InventoryService;
import co.lacava.gestion.inventory.application.LotAllocation;
import co.lacava.gestion.inventory.domain.MovementType;
import co.lacava.gestion.shared.error.InvalidFieldsException;
import co.lacava.gestion.support.IntegrationTest;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.Role;
import tools.jackson.databind.ObjectMapper;

/** LCV-HU-05: consumo FEFO, salidas manuales y baja de vencidos (backend). */
class OutputIntegrationTest extends IntegrationTest {

    private static final String OUTPUTS = "/api/v1/inventory/outputs";
    private static final String WRITE_OFF = "/api/v1/inventory/expired/write-off";
    /** 23:00 del 9 de marzo en Bogotá (ya 10 de marzo en UTC): "hoy" es 2026-03-09 para el negocio. */
    private static final Instant NOW = Instant.parse("2026-03-10T04:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-03-09");

    @Autowired
    private UserService users;

    @Autowired
    private InventoryService inventory;

    @Autowired
    private TransactionTemplate transactions;

    private final ObjectMapper json = new ObjectMapper();
    private final Map<Role, String> tokens = new HashMap<>();
    private long adminId;

    @BeforeEach
    void createUsersAndFixClock() throws Exception {
        adminId = users.create("admin", "Administrador", "admin123", Role.ADMIN).getId();
        users.create("camila", "Camila Torres", "camila123", Role.CASHIER);
        users.create("laura", "Laura Gómez", "laura123", Role.WAITER);
        users.create("jorge", "Jorge Ruiz", "jorge123", Role.KITCHEN);
        tokens.put(Role.ADMIN, tokenOf("admin"));
        tokens.put(Role.CASHIER, tokenOf("camila"));
        tokens.put(Role.WAITER, tokenOf("laura"));
        tokens.put(Role.KITCHEN, tokenOf("jorge"));
        // Los tokens se emiten con el reloj real (el decodificador JWT valida contra la hora real); luego se fija.
        clock.fixAt(NOW);
    }

    // ---------- CA1: consumo FEFO ----------

    @Test
    @DisplayName("CA1 una salida menor que el lote que vence antes solo descuenta de ese lote")
    void ca1SmallOutputTouchesOnlyFirstLot() throws Exception {
        long productId = createProduct("Copa de vino");
        long lotB = receive(productId, 10, TODAY.plusDays(20));
        long lotA = receive(productId, 5, TODAY.plusDays(2));

        as(Role.ADMIN, post(OUTPUTS), output(productId, 3, "WASTE_OUT", "  Botella rota  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.productName").value("Copa de vino"))
                .andExpect(jsonPath("$.type").value("WASTE_OUT"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.consumed.length()").value(1))
                .andExpect(jsonPath("$.consumed[0].lotId").value(lotA))
                .andExpect(jsonPath("$.consumed[0].expiresOn").value(TODAY.plusDays(2).toString()))
                .andExpect(jsonPath("$.consumed[0].quantity").value(3))
                .andExpect(jsonPath("$.available").value(12));

        assertThat(availableOf(lotA)).isEqualTo(2);
        assertThat(availableOf(lotB)).isEqualTo(10);
        List<Map<String, Object>> outputs = outputMovements();
        assertThat(outputs).hasSize(1);
        assertMovement(outputs.get(0), lotA, "WASTE_OUT", -3, "Botella rota");
    }

    @Test
    @DisplayName("CA1 una salida mayor que el primer lote se reparte A→B en orden de vencimiento e ignora los vencidos")
    void ca1LargeOutputSplitsAcrossLotsIgnoringExpired() throws Exception {
        long productId = createProduct("Copa de vino");
        long expired = inventory.loadSampleLot(productId, 4, TODAY.minusDays(1), null, NOW, adminId).getId();
        long lotB = receive(productId, 10, TODAY.plusDays(20));
        long lotA = receive(productId, 5, TODAY);

        as(Role.ADMIN, post(OUTPUTS), output(productId, 8, "INTERNAL_USE_OUT", "Cata del personal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumed[*].lotId").value(contains((int) lotA, (int) lotB)))
                .andExpect(jsonPath("$.consumed[*].quantity").value(contains(5, 3)))
                .andExpect(jsonPath("$.available").value(7));

        assertThat(availableOf(lotA)).isZero();
        assertThat(availableOf(lotB)).isEqualTo(7);
        assertThat(availableOf(expired)).isEqualTo(4);
        List<Map<String, Object>> outputs = outputMovements();
        assertThat(outputs).hasSize(2);
        assertMovement(outputs.get(0), lotA, "INTERNAL_USE_OUT", -5, "Cata del personal");
        assertMovement(outputs.get(1), lotB, "INTERNAL_USE_OUT", -3, "Cata del personal");
    }

    @Test
    @DisplayName("CA1 consume() con el mismo vencimiento sigue el orden por id y guarda order_id en cada movimiento")
    void ca1ConsumeServiceSameExpiryByIdWithOrder() throws Exception {
        long productId = createProduct("Copa de vino");
        long first = receive(productId, 2, TODAY.plusDays(5));
        long second = receive(productId, 2, TODAY.plusDays(5));

        List<LotAllocation> allocations = inventory.consume(productId, 3, MovementType.SALE, null, adminId, 77L);

        assertThat(allocations).containsExactly(new LotAllocation(first, TODAY.plusDays(5), 2),
                new LotAllocation(second, TODAY.plusDays(5), 1));
        assertThat(jdbcTemplate.queryForList(
                "SELECT order_id FROM stock_movement WHERE type = 'SALE' ORDER BY id", Long.class))
                .containsExactly(77L, 77L);
    }

    @ParameterizedTest
    @ValueSource(strings = { "LOT_IN", "ORDER_CANCEL_RETURN", "EXPIRED_WRITE_OFF" })
    @DisplayName("CA1 consume() rechaza con IllegalArgumentException un tipo que no es SALE ni de salida manual")
    void ca1ConsumeRejectsNonConsumptionType(MovementType type) throws Exception {
        long productId = createProduct("Copa de vino");
        long lot = receive(productId, 5, TODAY.plusDays(5));

        assertThatThrownBy(() -> inventory.consume(productId, 1, type, null, adminId, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertUnchanged(lot, 5);
    }

    // ---------- CA2: salidas manuales ----------

    @ParameterizedTest
    @ValueSource(strings = { "WASTE_OUT", "DAMAGED_OUT", "INTERNAL_USE_OUT", "ADJUSTMENT_OUT" })
    @DisplayName("CA2 cada tipo de salida válido se registra con su tipo y su observación")
    void ca2EachValidType(String type) throws Exception {
        long productId = createProduct("Copa de vino");
        long lot = receive(productId, 5, TODAY.plusDays(5));

        as(Role.ADMIN, post(OUTPUTS), output(productId, 5, type, "Motivo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value(type))
                .andExpect(jsonPath("$.available").value(0));

        assertMovement(outputMovements().get(0), lot, type, -5, "Motivo");
    }

    @ParameterizedTest
    @ValueSource(strings = { "LOT_IN", "SALE", "EXPIRED_WRITE_OFF", "MERMA", "" })
    @DisplayName("CA2 un tipo que no es de salida manual da 400 en type sin cambios")
    void ca2InvalidType(String type) throws Exception {
        long productId = createProduct("Copa de vino");
        long lot = receive(productId, 5, TODAY.plusDays(5));

        as(Role.ADMIN, post(OUTPUTS), output(productId, 1, type, "Motivo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("type")));

        assertUnchanged(lot, 5);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    @DisplayName("CA2 la observación vacía o en blanco da 400 en note sin cambios")
    void ca2NoteRequired(String note) throws Exception {
        long productId = createProduct("Copa de vino");
        long lot = receive(productId, 5, TODAY.plusDays(5));

        as(Role.ADMIN, post(OUTPUTS), output(productId, 1, "WASTE_OUT", note))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("note")))
                .andExpect(jsonPath("$.errors[0].message").value("La observación es obligatoria."));

        assertUnchanged(lot, 5);
    }

    @Test
    @DisplayName("CA2 sin observación, sin tipo, sin producto y cantidad 0 responde todos los errores juntos")
    void ca2AllErrorsTogether() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("quantity", 0);

        as(Role.ADMIN, post(OUTPUTS), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("productId", "quantity", "type", "note")));
    }

    @Test
    @DisplayName("CA2 observación de más de 140 caracteres (tras recortar) da 400 en note; 140 se acepta")
    void ca2NoteMaxLength() throws Exception {
        long productId = createProduct("Copa de vino");
        long lot = receive(productId, 5, TODAY.plusDays(5));

        as(Role.ADMIN, post(OUTPUTS), output(productId, 1, "WASTE_OUT", "x".repeat(141)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("note")));
        assertUnchanged(lot, 5);

        as(Role.ADMIN, post(OUTPUTS), output(productId, 1, "WASTE_OUT", " " + "x".repeat(140) + " "))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -2 })
    @DisplayName("CA2 cantidad 0 o negativa da 400 en quantity sin cambios")
    void ca2QuantityMustBePositive(int quantity) throws Exception {
        long productId = createProduct("Copa de vino");
        long lot = receive(productId, 5, TODAY.plusDays(5));

        as(Role.ADMIN, post(OUTPUTS), output(productId, quantity, "WASTE_OUT", "Motivo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("quantity")))
                .andExpect(jsonPath("$.errors[0].message").value("La cantidad debe ser mayor que 0."));

        assertUnchanged(lot, 5);
    }

    @Test
    @DisplayName("CA2 cantidad decimal (2.5) da 400 en quantity sin cambios (Jackson no la redondea)")
    void ca2DecimalQuantityRejected() throws Exception {
        long productId = createProduct("Copa de vino");
        long lot = receive(productId, 5, TODAY.plusDays(5));

        as(Role.ADMIN, post(OUTPUTS), output(productId, 2.5, "WASTE_OUT", "Motivo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("quantity")))
                .andExpect(jsonPath("$.errors[0].message").value("Debe ser un número entero."));

        assertUnchanged(lot, 5);
    }

    @Test
    @DisplayName("CA2 cantidad mayor que la vigente (los vencidos no cuentan) da 400 en quantity sin cambios")
    void ca2QuantityAboveAvailable() throws Exception {
        long productId = createProduct("Copa de vino");
        long lotA = receive(productId, 2, TODAY.plusDays(1));
        long lotB = receive(productId, 3, TODAY.plusDays(9));
        long expired = inventory.loadSampleLot(productId, 10, TODAY.minusDays(1), null, NOW, adminId).getId();

        as(Role.ADMIN, post(OUTPUTS), output(productId, 6, "ADJUSTMENT_OUT", "Conteo físico"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("quantity")))
                .andExpect(jsonPath("$.errors[0].message").value("Solo hay 5 disponibles."));

        assertThat(availableOf(lotA)).isEqualTo(2);
        assertThat(availableOf(lotB)).isEqualTo(3);
        assertThat(availableOf(expired)).isEqualTo(10);
        assertThat(outputMovements()).isEmpty();
    }

    @Test
    @DisplayName("CA2 producto inexistente da 404")
    void ca2MissingProduct() throws Exception {
        as(Role.ADMIN, post(OUTPUTS), output(999L, 1, "WASTE_OUT", "Motivo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El producto no existe."));

        assertThat(count("SELECT count(*) FROM stock_movement")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = { "CASHIER", "WAITER", "KITCHEN" })
    @DisplayName("CA2 CA3 salidas y baja de vencidos dan 403 a cajero, mesero y preparación")
    void ca2ForbiddenForNonAdmin(Role role) throws Exception {
        long productId = createProduct("Copa de vino");
        long lot = receive(productId, 5, TODAY.plusDays(5));
        long expired = inventory.loadSampleLot(productId, 2, TODAY.minusDays(1), null, NOW, adminId).getId();

        as(role, post(OUTPUTS), output(productId, 1, "WASTE_OUT", "Motivo")).andExpect(status().isForbidden());
        as(role, post(WRITE_OFF), null).andExpect(status().isForbidden());

        assertUnchanged(lot, 5);
        assertThat(availableOf(expired)).isEqualTo(2);
    }

    // ---------- CA3: baja de vencidos ----------

    @Test
    @DisplayName("CA3 deja en 0 los lotes vencidos de todos los productos con un EXPIRED_WRITE_OFF por lote; vigente intacto")
    void ca3WritesOffExpiredLots() throws Exception {
        long wine = createProduct("Copa de vino");
        long cheese = createProduct("Tabla de quesos");
        long expiredWine = inventory.loadSampleLot(wine, 4, TODAY.minusDays(1), null, NOW, adminId).getId();
        long expiredCheese = inventory.loadSampleLot(cheese, 3, TODAY.minusDays(10), null, NOW, adminId).getId();
        long emptyExpired = inventory.loadSampleLot(cheese, 2, TODAY.minusDays(3), null, NOW, adminId).getId();
        jdbcTemplate.update("UPDATE stock_lot SET quantity_available = 0 WHERE id = ?", emptyExpired);
        long current = receive(wine, 6, TODAY);

        as(Role.ADMIN, post(WRITE_OFF), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lots").value(2))
                .andExpect(jsonPath("$.units").value(7));

        assertThat(availableOf(expiredWine)).isZero();
        assertThat(availableOf(expiredCheese)).isZero();
        assertThat(availableOf(current)).isEqualTo(6);
        List<Map<String, Object>> writeOffs = jdbcTemplate.queryForList("""
                SELECT lot_id, product_id, quantity, user_id FROM stock_movement
                WHERE type = 'EXPIRED_WRITE_OFF' ORDER BY lot_id""");
        assertThat(writeOffs).hasSize(2);
        assertThat(((Number) writeOffs.get(0).get("lot_id")).longValue()).isEqualTo(expiredWine);
        assertThat(((Number) writeOffs.get(0).get("product_id")).longValue()).isEqualTo(wine);
        assertThat(writeOffs.get(0).get("quantity")).isEqualTo(-4);
        assertThat(((Number) writeOffs.get(1).get("lot_id")).longValue()).isEqualTo(expiredCheese);
        assertThat(((Number) writeOffs.get(1).get("product_id")).longValue()).isEqualTo(cheese);
        assertThat(writeOffs.get(1).get("quantity")).isEqualTo(-3);
        assertThat(writeOffs).allSatisfy(m -> assertThat(((Number) m.get("user_id")).longValue()).isEqualTo(adminId));

        // Segunda llamada: ya no queda nada vencido.
        as(Role.ADMIN, post(WRITE_OFF), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lots").value(0))
                .andExpect(jsonPath("$.units").value(0));
        assertThat(count("SELECT count(*) FROM stock_movement WHERE type = 'EXPIRED_WRITE_OFF'")).isEqualTo(2);
    }

    @Test
    @DisplayName("CA3 el lote que vence hoy en Bogotá no se da de baja; al día siguiente sí")
    void ca3ExpiresTodayIsNotExpired() throws Exception {
        long productId = createProduct("Copa de vino");
        long lot = receive(productId, 3, TODAY);

        as(Role.ADMIN, post(WRITE_OFF), null).andExpect(jsonPath("$.lots").value(0));
        assertThat(availableOf(lot)).isEqualTo(3);

        clock.fixAt(NOW.plusSeconds(86_400));
        as(Role.ADMIN, post(WRITE_OFF), null)
                .andExpect(jsonPath("$.lots").value(1))
                .andExpect(jsonPath("$.units").value(3));
        assertThat(availableOf(lot)).isZero();
    }

    // ---------- Concurrencia (D33) ----------

    @Test
    @DisplayName("CA1 dos salidas simultáneas que solo alcanzan para una: una 200 y otra 400; la existencia nunca es negativa")
    void ca1ConcurrentOutputsNeverGoNegative() throws Exception {
        long productId = createProduct("Copa de vino");
        long lot = receive(productId, 5, TODAY.plusDays(5));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return as(Role.ADMIN, post(OUTPUTS), output(productId, 4, "WASTE_OUT", "Carrera"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        } finally {
            pool.shutdownNow();
        }

        assertThat(availableOf(lot)).isEqualTo(1);
        assertThat(outputMovements()).hasSize(1);
    }

    @Test
    @DisplayName("CA1 consume() bloquea los lotes: un segundo consumo espera al primero y luego ve la existencia real")
    void ca1ConsumeWaitsForLockedLots() throws Exception {
        long productId = createProduct("Copa de vino");
        long lot = receive(productId, 5, TODAY.plusDays(5));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> transactions.executeWithoutResult(tx -> {
                inventory.consume(productId, 4, MovementType.WASTE_OUT, "Primero", adminId, null);
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();
            Future<?> second = pool.submit(
                    () -> inventory.consume(productId, 4, MovementType.WASTE_OUT, "Segundo", adminId, null));

            Thread.sleep(500);
            assertThat(second).isNotDone();

            release.countDown();
            first.get(30, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(30, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(InvalidFieldsException.class)
                    .cause()
                    .satisfies(e -> assertThat(((InvalidFieldsException) e).getErrors().get(0).message())
                            .isEqualTo("Solo hay 1 disponibles."));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        assertThat(availableOf(lot)).isEqualTo(1);
    }

    // ---------- Apoyo ----------

    private void assertMovement(Map<String, Object> movement, long lotId, String type, int quantity, String note) {
        assertThat(((Number) movement.get("lot_id")).longValue()).isEqualTo(lotId);
        assertThat(movement.get("type")).isEqualTo(type);
        assertThat(movement.get("quantity")).isEqualTo(quantity);
        assertThat(movement.get("note")).isEqualTo(note);
        assertThat(movement.get("order_id")).isNull();
        assertThat(((Number) movement.get("user_id")).longValue()).isEqualTo(adminId);
    }

    private List<Map<String, Object>> outputMovements() {
        return jdbcTemplate.queryForList("""
                SELECT lot_id, type, quantity, note, order_id, user_id FROM stock_movement
                WHERE type <> 'LOT_IN' ORDER BY id""");
    }

    private void assertUnchanged(long lotId, int quantity) {
        assertThat(availableOf(lotId)).isEqualTo(quantity);
        assertThat(outputMovements()).isEmpty();
    }

    private int availableOf(long lotId) {
        Integer value = jdbcTemplate.queryForObject("SELECT quantity_available FROM stock_lot WHERE id = ?",
                Integer.class, lotId);
        return value == null ? -1 : value;
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private static Map<String, Object> output(Long productId, Number quantity, String type, String note) {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("quantity", quantity);
        body.put("type", type);
        body.put("note", note);
        return body;
    }

    private long receive(long productId, int quantity, LocalDate expiresOn) {
        return inventory.receiveLot(productId, quantity, expiresOn, null, adminId).id();
    }

    private long createProduct(String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("categoryName", "Vinos");
        body.put("price", 20000);
        String response = as(Role.ADMIN, post("/api/v1/products"), body).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private ResultActions as(Role role, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.get(role));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private String tokenOf(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", username + "123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asString();
    }
}
