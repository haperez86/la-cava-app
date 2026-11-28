package co.lacava.gestion.notifications.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import co.lacava.gestion.inventory.application.InventoryService;
import co.lacava.gestion.support.IntegrationTest;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.Role;
import tools.jackson.databind.ObjectMapper;

/** LCV-HU-15: aviso "Nuevo pedido #N · Mesa M" a Preparación al crear un pedido (D43). */
class OrderCreatedNotificationIntegrationTest extends IntegrationTest {

    private static final String NOTIFICATIONS = "/api/v1/notifications";
    private static final Instant NOW = Instant.parse("2026-03-10T04:00:00Z");

    @Autowired
    private UserService users;

    @Autowired
    private InventoryService inventory;

    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> tokens = new HashMap<>();
    private long jorgeId;
    private long anaId;
    private long lauraId;
    private long tableId;
    private long wineId;

    @BeforeEach
    void setUp() throws Exception {
        long adminId = users.create("admin", "Administrador", "admin123", Role.ADMIN).getId();
        lauraId = users.create("laura", "Laura Gómez", "laura123", Role.WAITER).getId();
        jorgeId = users.create("jorge", "Jorge Ruiz", "jorge123", Role.KITCHEN).getId();
        anaId = users.create("anabel", "Anabel Soto", "anabel123", Role.KITCHEN).getId();
        long inactiveId = users.create("inactivo", "Inactivo Cocina", "inactivo123", Role.KITCHEN).getId();
        users.create("pablo", "Pablo Cano", "pablo123", Role.CASHIER);
        jdbcTemplate.update("UPDATE app_user SET active = FALSE WHERE id = ?", inactiveId);
        for (String username : List.of("admin", "laura", "jorge", "anabel", "pablo")) {
            tokens.put(username, tokenOf(username));
        }
        clock.fixAt(NOW);
        tableId = idOf(as("admin", post("/api/v1/tables"), Map.of("number", 3)));
        Map<String, Object> product = new HashMap<>();
        product.put("name", "Copa de vino tinto");
        product.put("categoryName", "Carta");
        product.put("price", 18000);
        wineId = idOf(as("admin", post("/api/v1/products"), product));
        inventory.receiveLot(wineId, 5, LocalDate.parse("2026-03-09").plusDays(30), null, adminId);
    }

    @Test
    @DisplayName("CA1 al crear un pedido se guarda un aviso por cada KITCHEN activo, no a inactivos ni a otros roles")
    void ca1StoresOneNotificationPerActiveKitchenUser() throws Exception {
        long id = createOrder(1);

        List<Long> recipients = jdbcTemplate.queryForList(
                "SELECT user_id FROM notification WHERE type = 'ORDER_CREATED' AND order_id = ? AND read_at IS NULL"
                        + " AND message = ? AND table_number = 3 ORDER BY user_id",
                Long.class, id, "Nuevo pedido #" + id + " · Mesa 3");
        assertThat(recipients).containsExactlyInAnyOrder(jorgeId, anaId);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification", Integer.class)).isEqualTo(2);
        as("jorge", get(NOTIFICATIONS + "/unread"), null).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("ORDER_CREATED"));
        as("laura", get(NOTIFICATIONS + "/unread"), null).andExpect(jsonPath("$.length()").value(0));
        as("pablo", get(NOTIFICATIONS + "/unread"), null).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("CA1 solo Preparación recibe el evento notification por el stream; todos reciben order-changed")
    void ca1OnlyKitchenReceivesNotificationEvent() throws Exception {
        MvcResult jorge = openStream("jorge");
        MvcResult laura = openStream("laura");
        MvcResult pablo = openStream("pablo");
        long id = createOrder(1);

        await().atMost(Duration.ofSeconds(5)).until(() -> body(jorge).contains("event:notification"));
        await().atMost(Duration.ofSeconds(5)).until(() -> body(laura).contains("event:order-changed")
                && body(pablo).contains("event:order-changed"));
        assertThat(body(jorge)).contains("Nuevo pedido #" + id + " · Mesa 3").contains("\"type\":\"ORDER_CREATED\"");
        assertThat(body(laura)).doesNotContain("event:notification");
        assertThat(body(pablo)).doesNotContain("event:notification");
    }

    @Test
    @DisplayName("CA1 un 422 al crear (sin existencias) no crea avisos ni emite eventos")
    void ca1FailedCreateCreatesNoNotificationNorEvent() throws Exception {
        MvcResult jorge = openStream("jorge");
        Map<String, Object> item = new HashMap<>();
        item.put("productId", wineId);
        item.put("quantity", 50);
        Map<String, Object> order = new HashMap<>();
        order.put("tableId", tableId);
        order.put("waiterId", lauraId);
        order.put("items", List.of(item));
        as("admin", post("/api/v1/orders"), order).andExpect(status().isUnprocessableContent());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification", Integer.class)).isZero();
        // Un pedido válido posterior llega primero: el rechazado no emitió nada antes.
        createOrder(1);
        await().atMost(Duration.ofSeconds(5)).until(() -> body(jorge).contains("event:notification"));
        assertThat(body(jorge).split("event:notification", -1)).hasSize(2);
        assertThat(body(jorge).split("event:order-changed", -1)).hasSize(2);
    }

    private static String body(MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private MvcResult openStream(String username) throws Exception {
        String response = as(username, post(NOTIFICATIONS + "/stream-ticket"), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ticket = json.readTree(response).get("ticket").asString();
        return mockMvc.perform(get(NOTIFICATIONS + "/stream").param("ticket", ticket)
                .accept(MediaType.TEXT_EVENT_STREAM)).andExpect(request().asyncStarted()).andReturn();
    }

    private long createOrder(int quantity) throws Exception {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", wineId);
        item.put("quantity", quantity);
        Map<String, Object> body = new HashMap<>();
        body.put("tableId", tableId);
        body.put("waiterId", lauraId);
        body.put("items", List.of(item));
        return idOf(as("admin", post("/api/v1/orders"), body));
    }

    private long idOf(ResultActions result) throws Exception {
        String response = result.andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
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
