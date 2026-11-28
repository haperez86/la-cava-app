package co.lacava.gestion.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import co.lacava.gestion.inventory.application.InventoryService;
import co.lacava.gestion.support.IntegrationTest;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.Role;
import tools.jackson.databind.ObjectMapper;

/** LCV-HU-11: listado con filtros y contadores, y cancelación con devolución a los lotes (D39). */
class OrderListCancelIntegrationTest extends IntegrationTest {

    private static final String ORDERS = "/api/v1/orders";
    /** 23:00 del 9 de marzo en Bogotá. */
    private static final Instant NOW = Instant.parse("2026-03-10T04:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-03-09");

    @Autowired
    private UserService users;

    @Autowired
    private InventoryService inventory;

    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> tokens = new HashMap<>();
    private long adminId;
    private long lauraId;
    private long andresId;

    @BeforeEach
    void setUp() throws Exception {
        adminId = users.create("admin", "Administrador", "admin123", Role.ADMIN).getId();
        users.create("camila", "Camila Torres", "camila123", Role.CASHIER);
        lauraId = users.create("laura", "Laura Gómez", "laura123", Role.WAITER).getId();
        andresId = users.create("andres", "Andrés Rojas", "andres123", Role.WAITER).getId();
        users.create("jorge", "Jorge Ruiz", "jorge123", Role.KITCHEN);
        for (String username : List.of("admin", "camila", "laura", "andres", "jorge")) {
            tokens.put(username, tokenOf(username));
        }
        clock.fixAt(NOW);
    }

    // ---------- CA1: listado, búsqueda y contadores ----------

    @Test
    @DisplayName("CA1 listado paginado del más reciente al más antiguo con filtro por estado y contadores por estado")
    void ca1ListFiltersByStatusAndSummary() throws Exception {
        long table = createTable(3);
        long wine = product("Copa de vino tinto", 18000);
        receive(wine, 50, TODAY.plusDays(30));
        long first = createOrder(table, lauraId, wine, 1);
        clock.fixAt(NOW.plusSeconds(60));
        long second = createOrder(table, lauraId, wine, 1);
        clock.fixAt(NOW.plusSeconds(120));
        long third = createOrder(table, andresId, wine, 1);
        jdbcTemplate.update("UPDATE customer_order SET status = 'READY' WHERE id = ?", second);
        as("camila", post(ORDERS + "/" + third + "/cancel"), Map.of("reason", "Cliente se fue")).andExpect(status().isOk());

        as("admin", get(ORDERS), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(contains((int) third, (int) second, (int) first)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].tableNumber").value(3))
                .andExpect(jsonPath("$.content[0].items", hasSize(1)));
        as("camila", get(ORDERS).param("status", "PENDING"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(contains((int) first)));
        as("admin", get(ORDERS).param("status", "CANCELLED"), null)
                .andExpect(jsonPath("$.content[*].id").value(contains((int) third)));
        as("admin", get(ORDERS).param("status", "NOPE"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));

        as("admin", get(ORDERS + "/summary"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.all").value(3))
                .andExpect(jsonPath("$.PENDING").value(1))
                .andExpect(jsonPath("$.IN_PREPARATION").value(0))
                .andExpect(jsonPath("$.READY").value(1))
                .andExpect(jsonPath("$.DELIVERED").value(0))
                .andExpect(jsonPath("$.CLOSED").value(0))
                .andExpect(jsonPath("$.CANCELLED").value(1));
    }

    @Test
    @DisplayName("CA1 q numérico busca por número de pedido o de mesa; q de texto, por mesero (sin mayúsculas); el resumen respeta q e ignora status")
    void ca1SearchByNumberTableOrWaiter() throws Exception {
        long table3 = createTable(3);
        long table7 = createTable(7);
        long wine = product("Copa de vino tinto", 18000);
        receive(wine, 50, TODAY.plusDays(30));
        long o1 = createOrder(table3, lauraId, wine, 1);
        long o2 = createOrder(table7, andresId, wine, 1);
        long o3 = createOrder(table3, andresId, wine, 1);
        jdbcTemplate.update("UPDATE customer_order SET status = 'DELIVERED' WHERE id = ?", o3);

        as("admin", get(ORDERS).param("q", Long.toString(o2)), null)
                .andExpect(jsonPath("$.content[*].id").value(contains((int) o2)));
        as("admin", get(ORDERS).param("q", "3"), null)
                .andExpect(jsonPath("$.content[*].id").value(contains((int) o3, (int) o1)));
        as("admin", get(ORDERS).param("q", "7"), null)
                .andExpect(jsonPath("$.content[*].id").value(contains((int) o2)));
        as("admin", get(ORDERS).param("q", "  ANDRÉS "), null)
                .andExpect(jsonPath("$.content[*].id").value(contains((int) o3, (int) o2)));
        as("admin", get(ORDERS).param("q", "laura").param("status", "PENDING"), null)
                .andExpect(jsonPath("$.content[*].id").value(contains((int) o1)));
        as("admin", get(ORDERS).param("q", "zzz"), null)
                .andExpect(jsonPath("$.totalElements").value(0));

        as("admin", get(ORDERS + "/summary").param("q", "andrés"), null)
                .andExpect(jsonPath("$.all").value(2))
                .andExpect(jsonPath("$.PENDING").value(1))
                .andExpect(jsonPath("$.DELIVERED").value(1));
        as("admin", get(ORDERS).param("from", "2026-03-10"), null)
                .andExpect(jsonPath("$.totalElements").value(0));
        as("admin", get(ORDERS).param("from", "2026-03-09").param("to", "2026-03-09"), null)
                .andExpect(jsonPath("$.totalElements").value(3));
        as("admin", get(ORDERS).param("from", "2026-03-10").param("to", "2026-03-09"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
    }

    @Test
    @DisplayName("CA1 el listado y el resumen son solo para Administrador y Cajero: mesero y preparación reciben 403")
    void ca1ListForbiddenForWaiterAndKitchen() throws Exception {
        for (String user : List.of("laura", "jorge")) {
            as(user, get(ORDERS), null).andExpect(status().isForbidden());
            as(user, get(ORDERS + "/summary"), null).andExpect(status().isForbidden());
        }
    }

    // ---------- CA2: cancelar ----------

    @Test
    @DisplayName("CA2 cancelar un pedido PENDING: guarda motivo y fecha, devuelve las cantidades a sus lotes exactos y registra un ORDER_CANCEL_RETURN por lote")
    void ca2CancelReturnsStockToExactLots() throws Exception {
        long table = createTable(3);
        long wine = product("Copa de vino tinto", 18000);
        long early = receive(wine, 3, TODAY.plusDays(5));
        long late = receive(wine, 10, TODAY.plusDays(30));
        long order = createOrder(table, lauraId, wine, 5);
        assertThat(availableOf(early)).isZero();
        assertThat(availableOf(late)).isEqualTo(8);
        clock.fixAt(NOW.plusSeconds(600));

        as("camila", post(ORDERS + "/" + order + "/cancel"), Map.of("reason", "  Cliente cambió de opinión  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Cliente cambió de opinión"))
                .andExpect(jsonPath("$.cancelledAt").value(NOW.plusSeconds(600).toString()));

        assertThat(availableOf(early)).isEqualTo(3);
        assertThat(availableOf(late)).isEqualTo(10);
        List<Map<String, Object>> returns = jdbcTemplate.queryForList("""
                SELECT lot_id, quantity, order_id, user_id FROM stock_movement
                WHERE type = 'ORDER_CANCEL_RETURN' ORDER BY lot_id""");
        assertThat(returns).hasSize(2);
        assertThat(((Number) returns.get(0).get("lot_id")).longValue()).isEqualTo(early);
        assertThat(((Number) returns.get(0).get("quantity")).intValue()).isEqualTo(3);
        assertThat(((Number) returns.get(1).get("lot_id")).longValue()).isEqualTo(late);
        assertThat(((Number) returns.get(1).get("quantity")).intValue()).isEqualTo(2);
        assertThat(returns).allSatisfy(m -> {
            assertThat(((Number) m.get("order_id")).longValue()).isEqualTo(order);
            assertThat(((Number) m.get("user_id")).longValue()).isNotEqualTo(adminId);
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cancel_reason FROM customer_order WHERE id = ?", String.class, order))
                .isEqualTo("Cliente cambió de opinión");
        as("admin", get(ORDERS + "/" + order), null)
                .andExpect(jsonPath("$.cancelReason").value("Cliente cambió de opinión"));
    }

    @Test
    @DisplayName("CA2 un lote que venció después del pedido recibe igual la cantidad devuelta (queda vencida)")
    void ca2ExpiredLotStillReceivesReturn() throws Exception {
        long table = createTable(3);
        long wine = product("Copa de vino tinto", 18000);
        long lot = receive(wine, 5, TODAY.plusDays(1));
        long order = createOrder(table, lauraId, wine, 4);
        assertThat(availableOf(lot)).isEqualTo(1);
        // Pasan 3 días: el lote ya venció.
        clock.fixAt(NOW.plus(Duration.ofDays(3)));
        assertThat(inventory.available(wine)).isZero();

        as("admin", post(ORDERS + "/" + order + "/cancel"), Map.of("reason", "Error de digitación"))
                .andExpect(status().isOk());

        assertThat(availableOf(lot)).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM stock_movement WHERE type = 'ORDER_CANCEL_RETURN' AND quantity = 4"))
                .isEqualTo(1);
        // Sigue vencido: no cuenta como existencia vigente.
        assertThat(inventory.available(wine)).isZero();
    }

    @Test
    @DisplayName("CA2 motivo vacío, solo espacios, ausente o de más de 120 caracteres → 400 en reason y el pedido sigue PENDING")
    void ca2InvalidReason() throws Exception {
        long table = createTable(3);
        long wine = product("Copa de vino tinto", 18000);
        long lot = receive(wine, 5, TODAY.plusDays(30));
        long order = createOrder(table, lauraId, wine, 2);

        Map<String, Object> missing = new HashMap<>();
        for (Object body : new Object[] { Map.of("reason", ""), Map.of("reason", "   "), missing,
                Map.of("reason", "x".repeat(121)) }) {
            as("admin", post(ORDERS + "/" + order + "/cancel"), body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("reason"));
        }
        as("admin", post(ORDERS + "/" + order + "/cancel"), Map.of("reason", "x".repeat(120)))
                .andExpect(status().isOk());
        assertThat(availableOf(lot)).isEqualTo(5);
    }

    @Test
    @DisplayName("CA2 solo Administrador y Cajero cancelan: mesero y preparación → 403; pedido inexistente → 404")
    void ca2OnlyAdminAndCashier() throws Exception {
        long table = createTable(3);
        long wine = product("Copa de vino tinto", 18000);
        receive(wine, 5, TODAY.plusDays(30));
        long order = createOrder(table, lauraId, wine, 1);

        for (String user : List.of("laura", "jorge")) {
            as(user, post(ORDERS + "/" + order + "/cancel"), Map.of("reason", "x")).andExpect(status().isForbidden());
        }
        assertThat(count("SELECT count(*) FROM customer_order WHERE status = 'PENDING'")).isEqualTo(1);
        as("admin", post(ORDERS + "/9999/cancel"), Map.of("reason", "x")).andExpect(status().isNotFound());
    }

    // ---------- CA3: transiciones inválidas ----------

    @Test
    @DisplayName("CA3 cancelar un pedido que no está PENDING → 409 \"Solo se puede cancelar un pedido pendiente.\" y no devuelve stock")
    void ca3CancelOutsidePendingIs409() throws Exception {
        long table = createTable(3);
        long wine = product("Copa de vino tinto", 18000);
        long lot = receive(wine, 10, TODAY.plusDays(30));
        long order = createOrder(table, lauraId, wine, 4);

        for (String state : List.of("IN_PREPARATION", "READY", "DELIVERED", "CLOSED")) {
            jdbcTemplate.update("UPDATE customer_order SET status = ? WHERE id = ?", state, order);
            as("admin", post(ORDERS + "/" + order + "/cancel"), Map.of("reason", "Tarde"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Solo se puede cancelar un pedido pendiente."));
        }
        assertThat(availableOf(lot)).isEqualTo(6);
        assertThat(count("SELECT count(*) FROM stock_movement WHERE type = 'ORDER_CANCEL_RETURN'")).isZero();

        // Cancelar dos veces: el segundo intento ya no está PENDING.
        jdbcTemplate.update("UPDATE customer_order SET status = 'PENDING' WHERE id = ?", order);
        as("admin", post(ORDERS + "/" + order + "/cancel"), Map.of("reason", "Primera")).andExpect(status().isOk());
        as("admin", post(ORDERS + "/" + order + "/cancel"), Map.of("reason", "Segunda"))
                .andExpect(status().isConflict());
        assertThat(availableOf(lot)).isEqualTo(10);
        assertThat(count("SELECT count(*) FROM stock_movement WHERE type = 'ORDER_CANCEL_RETURN'")).isEqualTo(1);
    }

    // ---------- helpers ----------

    private long createOrder(long tableId, long waiterId, long productId, int quantity) throws Exception {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", productId);
        item.put("quantity", quantity);
        Map<String, Object> body = new HashMap<>();
        body.put("tableId", tableId);
        body.put("waiterId", waiterId);
        body.put("items", List.of(item));
        String response = as("admin", post(ORDERS), body).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private long createTable(int number) throws Exception {
        String body = as("admin", post("/api/v1/tables"), Map.of("number", number))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long product(String name, int price) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("categoryName", "Carta");
        body.put("price", price);
        String response = as("admin", post("/api/v1/products"), body).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private long receive(long productId, int quantity, LocalDate expiresOn) {
        return inventory.receiveLot(productId, quantity, expiresOn, null, adminId).id();
    }

    private int availableOf(long lotId) {
        return jdbcTemplate.queryForObject("SELECT quantity_available FROM stock_lot WHERE id = ?", Integer.class,
                lotId);
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private ResultActions as(String username, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.get(username));
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
