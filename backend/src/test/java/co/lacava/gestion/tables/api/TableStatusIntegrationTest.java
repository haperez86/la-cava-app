package co.lacava.gestion.tables.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
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
import co.lacava.gestion.orders.application.NewOrder;
import co.lacava.gestion.orders.application.OrderService;
import co.lacava.gestion.support.IntegrationTest;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.Role;
import tools.jackson.databind.ObjectMapper;

/** LCV-HU-09: estado de mesas en {@code GET /tables} (especificación §3.3). */
class TableStatusIntegrationTest extends IntegrationTest {

    private static final String TABLES = "/api/v1/tables";
    private static final Instant NOW = Instant.parse("2026-03-10T01:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-03-09");

    @Autowired
    private UserService users;

    @Autowired
    private InventoryService inventory;

    @Autowired
    private OrderService orderService;

    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> tokens = new HashMap<>();
    private long adminId;
    private long lauraId;
    private long andresId;
    private long wine;
    private long tapas;
    private Instant orderClock;

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
        orderClock = NOW;
        wine = product("Copa de vino tinto", 18000);
        tapas = product("Tabla de quesos", 32500);
        inventory.receiveLot(wine, 100, TODAY.plusDays(30), null, adminId);
        inventory.receiveLot(tapas, 100, TODAY.plusDays(30), null, adminId);
    }

    @Test
    @DisplayName("CA1 mesa sin pedidos abiertos: FREE, stage null, openOrders 0, total 0 y waiterName null")
    void ca1FreeTable() throws Exception {
        createTable(1);

        as("admin", get(TABLES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].status").value("FREE"))
                .andExpect(jsonPath("$[0].stage").value(nullValue()))
                .andExpect(jsonPath("$[0].openOrders").value(0))
                .andExpect(jsonPath("$[0].total").value(0))
                .andExpect(jsonPath("$[0].waiterName").value(nullValue()));
    }

    @Test
    @DisplayName("CA1 la etapa es la más urgente: READY > IN_PREPARATION > PENDING > SERVED (SERVED = todos DELIVERED)")
    void ca1StagePriority() throws Exception {
        long tableId = createTable(4);
        long pending = order(tableId, lauraId, wine, 1);
        long inPreparation = order(tableId, lauraId, wine, 1);
        long ready = order(tableId, lauraId, wine, 1);
        long delivered = order(tableId, lauraId, wine, 1);
        setStatus(inPreparation, "IN_PREPARATION");
        setStatus(ready, "READY");
        setStatus(delivered, "DELIVERED");

        expectStage("READY", 4);
        setStatus(ready, "DELIVERED");
        expectStage("IN_PREPARATION", 4);
        setStatus(inPreparation, "DELIVERED");
        expectStage("PENDING", 4);
        setStatus(pending, "DELIVERED");
        expectStage("SERVED", 4);
    }

    @Test
    @DisplayName("CA1 CANCELLED y CLOSED no cuentan: la mesa con solo esos pedidos queda FREE y no suman al conteo ni al total")
    void ca1CancelledAndClosedDoNotCount() throws Exception {
        long onlyClosed = createTable(2);
        long mixed = createTable(3);
        setStatus(order(onlyClosed, lauraId, wine, 1), "CLOSED");
        setStatus(order(onlyClosed, lauraId, wine, 1), "CANCELLED");
        long served = order(mixed, lauraId, wine, 2);
        setStatus(served, "DELIVERED");
        setStatus(order(mixed, andresId, tapas, 1), "CANCELLED");
        setStatus(order(mixed, andresId, tapas, 3), "CLOSED");

        as("cashier", get(TABLES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].number", contains(2, 3)))
                .andExpect(jsonPath("$[0].status").value("FREE"))
                .andExpect(jsonPath("$[0].stage").value(nullValue()))
                .andExpect(jsonPath("$[0].openOrders").value(0))
                .andExpect(jsonPath("$[0].total").value(0))
                .andExpect(jsonPath("$[0].waiterName").value(nullValue()))
                .andExpect(jsonPath("$[1].status").value("OCCUPIED"))
                .andExpect(jsonPath("$[1].stage").value("SERVED"))
                .andExpect(jsonPath("$[1].openOrders").value(1))
                .andExpect(jsonPath("$[1].total").value(36000.0))
                .andExpect(jsonPath("$[1].waiterName").value("Laura Gómez"));
    }

    @Test
    @DisplayName("CA1 openOrders y total suman los pedidos abiertos (varios ítems) y waiterName es el mesero del pedido abierto más reciente")
    void ca1CountTotalAndLatestWaiter() throws Exception {
        long first = createTable(5);
        long second = createTable(6);
        // Mesa 5: Laura (2 vinos + 1 tabla), luego Andrés (1 tabla), luego Laura con un pedido cancelado.
        orderService.create(new NewOrder(first, lauraId,
                List.of(new NewOrder.Item(wine, 2, null), new NewOrder.Item(tapas, 1, null)), null, null),
                adminId, Role.ADMIN);
        advance();
        long byAndres = order(first, andresId, tapas, 1);
        setStatus(byAndres, "IN_PREPARATION");
        setStatus(order(first, lauraId, wine, 5), "CANCELLED");
        // Mesa 6: Andrés y luego Laura.
        order(second, andresId, wine, 1);
        order(second, lauraId, wine, 1);

        for (String username : List.of("admin", "cashier", "laura")) {
            as(username, get(TABLES))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].number", contains(5, 6)))
                    .andExpect(jsonPath("$[0].status").value("OCCUPIED"))
                    .andExpect(jsonPath("$[0].stage").value("IN_PREPARATION"))
                    .andExpect(jsonPath("$[0].openOrders").value(2))
                    .andExpect(jsonPath("$[0].total").value(101000.0))
                    .andExpect(jsonPath("$[0].waiterName").value("Andrés Rojas"))
                    .andExpect(jsonPath("$[1].stage").value("PENDING"))
                    .andExpect(jsonPath("$[1].openOrders").value(2))
                    .andExpect(jsonPath("$[1].total").value(36000.0))
                    .andExpect(jsonPath("$[1].waiterName").value("Laura Gómez"));
        }
    }

    @Test
    @DisplayName("CA1 GET /tables con estado para ADMIN, CASHIER y WAITER, sin token ni PIN; KITCHEN recibe 403")
    void ca1AccessAndNoSecrets() throws Exception {
        long tableId = createTable(8);
        createTable(9);
        order(tableId, lauraId, wine, 1);

        for (String username : List.of("admin", "cashier", "laura", "andres")) {
            String body = as(username, get(TABLES))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].status", contains("OCCUPIED", "FREE")))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("qrToken", "\"pin\"", "LACAVA", "tablePass");
            for (String secret : jdbcTemplate.queryForList("SELECT qr_token FROM dining_table", String.class)) {
                assertThat(body).doesNotContain(secret);
            }
        }
        as("jorge", get(TABLES)).andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private void expectStage(String stage, int openOrders) throws Exception {
        as("admin", get(TABLES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OCCUPIED"))
                .andExpect(jsonPath("$[0].stage").value(stage))
                .andExpect(jsonPath("$[0].openOrders").value(openOrders))
                .andExpect(jsonPath("$[0].total").value(18000.0 * openOrders))
                .andExpect(jsonPath("$[0].waiterName").value("Laura Gómez"));
    }

    /** Pedido de un producto registrado por el administrador; cada pedido nuevo es un minuto más reciente. */
    private long order(long tableId, long waiterId, long productId, int quantity) {
        long id = orderService.create(new NewOrder(tableId, waiterId,
                List.of(new NewOrder.Item(productId, quantity, null)), null, null), adminId, Role.ADMIN).id();
        advance();
        return id;
    }

    private void advance() {
        orderClock = orderClock.plus(Duration.ofMinutes(1));
        clock.fixAt(orderClock);
    }

    private void setStatus(long orderId, String status) {
        if (status.equals("CANCELLED")) {
            jdbcTemplate.update("UPDATE customer_order SET status = ?, cancel_reason = 'Prueba', cancelled_at = now() "
                    + "WHERE id = ?", status, orderId);
        } else {
            jdbcTemplate.update("UPDATE customer_order SET status = ? WHERE id = ?", status, orderId);
        }
    }

    private long createTable(int number) throws Exception {
        String body = as("admin", post(TABLES), Map.of("number", number))
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

    private ResultActions as(String username, MockHttpServletRequestBuilder request) throws Exception {
        return as(username, request, null);
    }

    private ResultActions as(String username, MockHttpServletRequestBuilder request, Object body) throws Exception {
        String key = username.equals("cashier") ? "camila" : username;
        request.header("Authorization", "Bearer " + tokens.get(key));
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
