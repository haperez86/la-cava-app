package co.lacava.gestion.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import co.lacava.gestion.inventory.application.InventoryService;
import co.lacava.gestion.support.IntegrationTest;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.Role;
import tools.jackson.databind.ObjectMapper;

/** LCV-HU-04: entradas por lote (backend). */
class LotIntegrationTest extends IntegrationTest {

    private static final String LOTS = "/api/v1/inventory/lots";
    private static final String PRODUCTS = "/api/v1/products";
    /** 23:00 del 9 de marzo en Bogotá (ya 10 de marzo en UTC): "hoy" es 2026-03-09 para el negocio. */
    private static final Instant NOW = Instant.parse("2026-03-10T04:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-03-09");

    @Autowired
    private UserService users;

    @Autowired
    private InventoryService inventory;

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

    // ---------- CA1: registrar un lote ----------

    @Test
    @DisplayName("CA1 sin temporada el lote toma la del producto; se crean stock_lot y LOT_IN con el usuario")
    void ca1ReceivesLotWithProductSeason() throws Exception {
        long productId = createProduct("Vino caliente navideño", "Navidad", TODAY.plusDays(30));

        as(Role.ADMIN, post(LOTS), lot(productId, 12, TODAY.plusDays(10), null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.productName").value("Vino caliente navideño"))
                .andExpect(jsonPath("$.quantityReceived").value(12))
                .andExpect(jsonPath("$.quantityAvailable").value(12))
                .andExpect(jsonPath("$.expiresOn").value(TODAY.plusDays(10).toString()))
                .andExpect(jsonPath("$.seasonName").value("Navidad"))
                .andExpect(jsonPath("$.receivedAt").value("2026-03-10T04:00:00Z"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT product_id, quantity_received, quantity_available, expires_on, season_name, created_by "
                        + "FROM stock_lot");
        assertThat(((Number) stored.get("product_id")).longValue()).isEqualTo(productId);
        assertThat(stored.get("quantity_received")).isEqualTo(12);
        assertThat(stored.get("quantity_available")).isEqualTo(12);
        assertThat(stored.get("expires_on").toString()).isEqualTo(TODAY.plusDays(10).toString());
        assertThat(stored.get("season_name")).isEqualTo("Navidad");
        assertThat(((Number) stored.get("created_by")).longValue()).isEqualTo(adminId);
        assertLotInMovement(productId, 12);
    }

    @Test
    @DisplayName("CA1 temporada en blanco también toma la del producto")
    void ca1BlankSeasonTakesProductSeason() throws Exception {
        long productId = createProduct("Vino caliente navideño", "Navidad", TODAY.plusDays(30));

        as(Role.ADMIN, post(LOTS), lot(productId, 3, TODAY.plusDays(10), "   "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seasonName").value("Navidad"));
    }

    @Test
    @DisplayName("CA1 con temporada explícita el lote la guarda; producto sin temporada y lote sin ella queda nulo")
    void ca1ReceivesLotWithExplicitSeason() throws Exception {
        long withSeason = createProduct("Sangría", "Verano", TODAY.plusDays(30));
        long plain = createProduct("Copa de vino", null, null);

        as(Role.ADMIN, post(LOTS), lot(withSeason, 5, TODAY.plusDays(10), "  Semana Santa "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seasonName").value("Semana Santa"));
        as(Role.ADMIN, post(LOTS), lot(plain, 5, TODAY.plusDays(10), null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seasonName").doesNotExist());

        assertThat(jdbcTemplate.queryForObject("SELECT season_name FROM stock_lot WHERE product_id = ?",
                String.class, withSeason)).isEqualTo("Semana Santa");
        assertLotInMovement(withSeason, 5);
    }

    @Test
    @DisplayName("CA1 vencimiento igual a hoy en Bogotá (aunque en UTC ya sea mañana) se acepta")
    void ca1ExpiresTodayAccepted() throws Exception {
        long productId = createProduct("Copa de vino", null, null);

        as(Role.ADMIN, post(LOTS), lot(productId, 2, TODAY, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EXPIRING"));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -3 })
    @DisplayName("CA1 cantidad 0 o negativa da 400 con el error en quantity y no crea nada")
    void ca1QuantityMustBePositive(int quantity) throws Exception {
        long productId = createProduct("Copa de vino", null, null);

        as(Role.ADMIN, post(LOTS), lot(productId, quantity, TODAY.plusDays(5), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("quantity")))
                .andExpect(jsonPath("$.errors[0].message").value("La cantidad debe ser mayor que 0."));

        assertNothingCreated();
    }

    @Test
    @DisplayName("CA1 vencimiento faltante da 400 con el error en expiresOn y no crea nada")
    void ca1MissingExpiresOn() throws Exception {
        long productId = createProduct("Copa de vino", null, null);

        as(Role.ADMIN, post(LOTS), lot(productId, 5, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("expiresOn")))
                .andExpect(jsonPath("$.errors[0].message").value("La fecha de vencimiento es obligatoria."));

        assertNothingCreated();
    }

    @Test
    @DisplayName("CA1 vencimiento anterior a hoy (según el Clock y la zona de Bogotá) da 400 en expiresOn y no crea nada")
    void ca1ExpiresOnBeforeToday() throws Exception {
        long productId = createProduct("Copa de vino", null, null);

        as(Role.ADMIN, post(LOTS), lot(productId, 5, TODAY.minusDays(1), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("expiresOn")))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("La fecha de vencimiento no puede ser anterior a hoy."));

        assertNothingCreated();
    }

    @Test
    @DisplayName("CA1 productId faltante y temporada de más de 40 caracteres dan 400 por campo")
    void ca1ProductAndSeasonValidation() throws Exception {
        as(Role.ADMIN, post(LOTS), lot(null, 5, TODAY.plusDays(5), "x".repeat(41)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("productId", "seasonName")));

        assertNothingCreated();
    }

    @Test
    @DisplayName("CA1 producto inexistente da 404 y no crea nada")
    void ca1MissingProduct() throws Exception {
        as(Role.ADMIN, post(LOTS), lot(999L, 5, TODAY.plusDays(5), null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El producto no existe."));

        assertNothingCreated();
    }

    @ParameterizedTest
    @ValueSource(strings = { "CASHIER", "WAITER", "KITCHEN" })
    @DisplayName("CA1 POST /inventory/lots y GET /products/{id}/lots dan 403 a cajero, mesero y preparación")
    void ca1ForbiddenForNonAdmin(Role role) throws Exception {
        long productId = createProduct("Copa de vino", null, null);

        as(role, post(LOTS), lot(productId, 5, TODAY.plusDays(5), null)).andExpect(status().isForbidden());
        as(role, get(PRODUCTS + "/" + productId + "/lots"), null).andExpect(status().isForbidden());

        assertNothingCreated();
    }

    // ---------- CA2: existencia vigente y estado de los lotes ----------

    @Test
    @DisplayName("CA2 con Clock fijo, available excluye el lote que vence ayer e incluye el que vence hoy")
    void ca2AvailableExcludesExpiredLots() throws Exception {
        long productId = createProduct("Postre de la casa", null, null);
        receive(productId, 3, TODAY);
        receive(productId, 4, TODAY.plusDays(1));
        inventory.loadSampleLot(productId, 2, TODAY.minusDays(1), null, NOW, adminId);

        as(Role.WAITER, get(PRODUCTS), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].available").value(7));

        // Al día siguiente el lote de "hoy" ya venció.
        clock.fixAt(NOW.plusSeconds(86_400));
        as(Role.WAITER, get(PRODUCTS), null)
                .andExpect(jsonPath("$.content[0].available").value(4));
    }

    @Test
    @DisplayName("CA2 GET /products/{id}/lots devuelve ACTIVE, EXPIRING, EXPIRED y CONSUMED ordenados por vencimiento")
    void ca2LotStatuses() throws Exception {
        long productId = createProduct("Tabla de quesos", "Temporada", TODAY.plusDays(60));
        long active = receive(productId, 5, TODAY.plusDays(4));
        long expiring = receive(productId, 5, TODAY.plusDays(3));
        long expiringToday = receive(productId, 5, TODAY);
        long consumed = receive(productId, 5, TODAY.plusDays(20));
        jdbcTemplate.update("UPDATE stock_lot SET quantity_available = 0 WHERE id = ?", consumed);
        long expired = inventory.loadSampleLot(productId, 2, TODAY.minusDays(1), null, NOW, adminId).getId();
        long expiredAndEmpty = inventory.loadSampleLot(productId, 2, TODAY.minusDays(2), null, NOW, adminId).getId();
        jdbcTemplate.update("UPDATE stock_lot SET quantity_available = 0 WHERE id = ?", expiredAndEmpty);
        long otherProduct = createProduct("Copa de vino", null, null);
        receive(otherProduct, 5, TODAY.plusDays(5));

        as(Role.ADMIN, get(PRODUCTS + "/" + productId + "/lots"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[*].id").value(contains((int) expiredAndEmpty, (int) expired,
                        (int) expiringToday, (int) expiring, (int) active, (int) consumed)))
                .andExpect(jsonPath("$[*].status").value(contains("CONSUMED", "EXPIRED", "EXPIRING", "EXPIRING",
                        "ACTIVE", "CONSUMED")))
                .andExpect(jsonPath("$[0].productName").value("Tabla de quesos"))
                .andExpect(jsonPath("$[0].seasonName").value("Temporada"))
                .andExpect(jsonPath("$[1].quantityReceived").value(2))
                .andExpect(jsonPath("$[1].quantityAvailable").value(2));
    }

    @Test
    @DisplayName("CA2 los lotes con el mismo vencimiento se ordenan por id")
    void ca2SameExpiryOrderedById() throws Exception {
        long productId = createProduct("Copa de vino", null, null);
        long first = receive(productId, 1, TODAY.plusDays(9));
        long second = receive(productId, 2, TODAY.plusDays(9));

        as(Role.ADMIN, get(PRODUCTS + "/" + productId + "/lots"), null)
                .andExpect(jsonPath("$[*].id").value(contains((int) first, (int) second)));
    }

    @Test
    @DisplayName("CA2 GET /products/{id}/lots de un producto inexistente da 404; sin lotes, lista vacía")
    void ca2LotsOfMissingProduct() throws Exception {
        long productId = createProduct("Copa de vino", null, null);

        as(Role.ADMIN, get(PRODUCTS + "/999/lots"), null).andExpect(status().isNotFound());
        as(Role.ADMIN, get(PRODUCTS + "/" + productId + "/lots"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- Apoyo ----------

    private void assertLotInMovement(long productId, int quantity) {
        Map<String, Object> movement = jdbcTemplate.queryForMap("""
                SELECT m.type, m.quantity, m.user_id, m.lot_id, l.id AS lot
                FROM stock_movement m JOIN stock_lot l ON l.id = m.lot_id
                WHERE m.product_id = ?""", productId);
        assertThat(movement.get("type")).isEqualTo("LOT_IN");
        assertThat(movement.get("quantity")).isEqualTo(quantity);
        assertThat(((Number) movement.get("user_id")).longValue()).isEqualTo(adminId);
        assertThat(movement.get("lot_id")).isEqualTo(movement.get("lot"));
    }

    private void assertNothingCreated() {
        assertThat(count("SELECT count(*) FROM stock_lot")).isZero();
        assertThat(count("SELECT count(*) FROM stock_movement")).isZero();
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private static Map<String, Object> lot(Long productId, int quantity, LocalDate expiresOn, String season) {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("quantity", quantity);
        body.put("expiresOn", expiresOn == null ? null : expiresOn.toString());
        body.put("seasonName", season);
        return body;
    }

    private long receive(long productId, int quantity, LocalDate expiresOn) throws Exception {
        String response = as(Role.ADMIN, post(LOTS), lot(productId, quantity, expiresOn, null))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private long createProduct(String name, String seasonName, LocalDate seasonUntil) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("categoryName", "Vinos");
        body.put("price", 20000);
        if (seasonName != null) {
            body.put("seasonName", seasonName);
            body.put("seasonUntil", seasonUntil.toString());
        }
        String response = as(Role.ADMIN, post(PRODUCTS), body).andExpect(status().isCreated()).andReturn()
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
