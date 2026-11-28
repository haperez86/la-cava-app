package co.lacava.gestion.notifications.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import co.lacava.gestion.inventory.application.InventoryService;
import co.lacava.gestion.support.IntegrationTest;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.Role;
import tools.jackson.databind.ObjectMapper;

/** LCV-HU-14: aviso de pedido listo al mesero, avisos no leídos, entrega y reenvío (D42). */
class OrderReadyNotificationIntegrationTest extends IntegrationTest {

    private static final String ORDERS = "/api/v1/orders/";
    private static final String NOTIFICATIONS = "/api/v1/notifications";
    private static final Instant NOW = Instant.parse("2026-03-10T04:00:00Z");

    @Autowired
    private UserService users;

    @Autowired
    private InventoryService inventory;

    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> tokens = new HashMap<>();
    private long lauraId;
    private long tableId;
    private long wineId;

    @BeforeEach
    void setUp() throws Exception {
        long adminId = users.create("admin", "Administrador", "admin123", Role.ADMIN).getId();
        lauraId = users.create("laura", "Laura Gómez", "laura123", Role.WAITER).getId();
        users.create("marta", "Marta Ríos", "marta123", Role.WAITER);
        users.create("jorge", "Jorge Ruiz", "jorge123", Role.KITCHEN);
        users.create("pablo", "Pablo Cano", "pablo123", Role.CASHIER);
        for (String username : List.of("admin", "laura", "marta", "jorge", "pablo")) {
            tokens.put(username, tokenOf(username));
        }
        clock.fixAt(NOW);
        tableId = idOf(as("admin", post("/api/v1/tables"), Map.of("number", 3)));
        Map<String, Object> product = new HashMap<>();
        product.put("name", "Copa de vino tinto");
        product.put("categoryName", "Carta");
        product.put("price", 18000);
        wineId = idOf(as("admin", post("/api/v1/products"), product));
        inventory.receiveLot(wineId, 100, LocalDate.parse("2026-03-09").plusDays(30), null, adminId);
    }

    @Test
    @DisplayName("CA1 al marcar listo, solo el mesero del pedido recibe el evento notification y el aviso queda persistido")
    void ca1OnlyOrderWaiterReceivesNotification() throws Exception {
        long id = createOrder();
        MvcResult laura = openStream("laura");
        MvcResult marta = openStream("marta");
        as("jorge", post(ORDERS + id + "/start"), null).andExpect(status().isOk());
        as("jorge", post(ORDERS + id + "/ready"), null).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(5))
                .until(() -> body(laura).contains("\"tableNumber\":3"));
        // order-changed llega a todos: cuando Marta ya lo vio READY, su notification (si hubiera) ya habría salido.
        await().atMost(Duration.ofSeconds(5))
                .until(() -> marta.getResponse().getContentAsString().contains("\"status\":\"READY\""));
        String lauraBody = body(laura);
        assertThat(lauraBody).contains("Pedido #" + id + " listo · Mesa 3").contains("\"type\":\"ORDER_READY\"")
                .contains("\"orderId\":" + id).contains("\"tableNumber\":3");
        assertThat(marta.getResponse().getContentAsString()).doesNotContain("event:notification");

        Integer persisted = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification WHERE user_id = ? AND order_id = ? AND type = 'ORDER_READY'"
                        + " AND read_at IS NULL AND message = ?",
                Integer.class, lauraId, id, "Pedido #" + id + " listo · Mesa 3");
        assertThat(persisted).isEqualTo(1);
    }

    @Test
    @DisplayName("CA1 si marcar listo falla (409) no se crea ningún aviso")
    void ca1FailedReadyCreatesNoNotification() throws Exception {
        long id = createOrder();
        as("jorge", post(ORDERS + id + "/ready"), null).andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification WHERE type = 'ORDER_READY'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("CA2 sin conexión: unread devuelve mis avisos y no los de otro; read ajeno → 404; read propio es idempotente")
    void ca2UnreadAndRead() throws Exception {
        long id = readyOrder();
        as("laura", get(NOTIFICATIONS + "/unread"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].message").value("Pedido #" + id + " listo · Mesa 3"))
                .andExpect(jsonPath("$[0].orderId").value(id)).andExpect(jsonPath("$[0].tableNumber").value(3))
                .andExpect(jsonPath("$[0].type").value("ORDER_READY")).andExpect(jsonPath("$[0].readAt").doesNotExist());
        as("marta", get(NOTIFICATIONS + "/unread"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        long notificationId = jdbcTemplate.queryForObject("SELECT id FROM notification WHERE type = 'ORDER_READY'", Long.class);

        as("marta", post(NOTIFICATIONS + "/" + notificationId + "/read"), null).andExpect(status().isNotFound());
        as("laura", get(NOTIFICATIONS + "/unread"), null).andExpect(jsonPath("$.length()").value(1));
        as("laura", post(NOTIFICATIONS + "/999/read"), null).andExpect(status().isNotFound());

        as("laura", post(NOTIFICATIONS + "/" + notificationId + "/read"), null).andExpect(status().isOk());
        as("laura", post(NOTIFICATIONS + "/" + notificationId + "/read"), null).andExpect(status().isOk());
        as("laura", get(NOTIFICATIONS + "/unread"), null).andExpect(jsonPath("$.length()").value(0));
        as("laura", get(NOTIFICATIONS + "/unread"), null).andExpect(status().isOk());
        mockMvc.perform(get(NOTIFICATIONS + "/unread")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CA2 GET /orders/mine devuelve solo los pedidos propios no entregados; otros roles → 403")
    void ca2MineListsOwnUndeliveredOrders() throws Exception {
        long ready = readyOrder();
        long pending = createOrder();
        long delivered = readyOrder();
        as("laura", post(ORDERS + delivered + "/deliver"), null).andExpect(status().isOk());
        as("laura", get(ORDERS + "mine"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(ready)).andExpect(jsonPath("$[0].status").value("READY"))
                .andExpect(jsonPath("$[1].id").value(pending));
        as("marta", get(ORDERS + "mine"), null).andExpect(jsonPath("$.length()").value(0));
        as("jorge", get(ORDERS + "mine"), null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA3 el mesero entrega su pedido: DELIVERED, avisos leídos y order-changed emitido")
    void ca3DeliverMarksReadAndEmits() throws Exception {
        long id = readyOrder();
        MvcResult stream = openStream("marta");
        as("laura", post(ORDERS + id + "/deliver"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification WHERE type = 'ORDER_READY' AND read_at IS NULL",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT delivered_at IS NOT NULL FROM customer_order WHERE id = ?",
                Boolean.class, id)).isTrue();
        as("laura", get(NOTIFICATIONS + "/unread"), null).andExpect(jsonPath("$.length()").value(0));
        await().atMost(Duration.ofSeconds(5))
                .until(() -> stream.getResponse().getContentAsString().contains("\"status\":\"DELIVERED\""));
    }

    @Test
    @DisplayName("CA3 deliver: ajeno → 404, fuera de READY → 409, cocina y caja → 403; el administrador puede")
    void ca3DeliverRules() throws Exception {
        long pending = createOrder();
        as("laura", post(ORDERS + pending + "/deliver"), null).andExpect(status().isConflict());
        long id = readyOrder();
        as("marta", post(ORDERS + id + "/deliver"), null).andExpect(status().isNotFound());
        as("jorge", post(ORDERS + id + "/deliver"), null).andExpect(status().isForbidden());
        as("pablo", post(ORDERS + id + "/deliver"), null).andExpect(status().isForbidden());
        as("laura", post(ORDERS + 9999 + "/deliver"), null).andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM customer_order WHERE id = ?", String.class, id))
                .isEqualTo("READY");
        as("admin", post(ORDERS + id + "/deliver"), null).andExpect(status().isOk());
        as("laura", post(ORDERS + id + "/deliver"), null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CA4 notify-again crea un aviso nuevo y lo envía solo al mesero; fuera de READY → 409; mesero → 403")
    void ca4NotifyAgainCreatesNewNotification() throws Exception {
        long pending = createOrder();
        as("jorge", post(ORDERS + pending + "/notify-again"), null).andExpect(status().isConflict());
        long id = readyOrder();
        MvcResult laura = openStream("laura");
        as("laura", post(ORDERS + id + "/notify-again"), null).andExpect(status().isForbidden());
        as("jorge", post(ORDERS + id + "/notify-again"), null).andExpect(status().isNoContent());

        await().atMost(Duration.ofSeconds(5))
                .until(() -> body(laura).contains("event:notification"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification WHERE type = 'ORDER_READY' AND order_id = ?",
                Integer.class, id)).isEqualTo(2);
        as("laura", get(NOTIFICATIONS + "/unread"), null).andExpect(jsonPath("$.length()").value(2));
        as("jorge", post(ORDERS + 9999 + "/notify-again"), null).andExpect(status().isNotFound());
    }

    /** El stream va en UTF-8 (los avisos llevan "·"); MockMvc lo decodificaría como ISO-8859-1 por defecto. */
    private static String body(MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private long readyOrder() throws Exception {
        long id = createOrder();
        as("jorge", post(ORDERS + id + "/start"), null).andExpect(status().isOk());
        as("jorge", post(ORDERS + id + "/ready"), null).andExpect(status().isOk());
        return id;
    }

    private MvcResult openStream(String username) throws Exception {
        String response = as(username, post(NOTIFICATIONS + "/stream-ticket"), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ticket = json.readTree(response).get("ticket").asString();
        return mockMvc.perform(get(NOTIFICATIONS + "/stream").param("ticket", ticket)
                .accept(MediaType.TEXT_EVENT_STREAM)).andExpect(request().asyncStarted()).andReturn();
    }

    private long createOrder() throws Exception {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", wineId);
        item.put("quantity", 1);
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
