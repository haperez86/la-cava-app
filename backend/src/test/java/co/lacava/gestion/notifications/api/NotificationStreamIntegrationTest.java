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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.lacava.gestion.inventory.application.InventoryService;
import co.lacava.gestion.notifications.application.StreamTicketService;
import co.lacava.gestion.orders.application.OrderChanged;
import co.lacava.gestion.orders.domain.OrderStatus;
import co.lacava.gestion.support.IntegrationTest;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.Role;
import tools.jackson.databind.ObjectMapper;

/** LCV-HU-13: ticket, canal SSE y emisión después del commit (D7, D41). */
class NotificationStreamIntegrationTest extends IntegrationTest {

    private static final String TICKET = "/api/v1/notifications/stream-ticket";
    private static final String STREAM = "/api/v1/notifications/stream";
    private static final Instant NOW = Instant.parse("2026-03-10T04:00:00Z");

    @Autowired
    private UserService users;

    @Autowired
    private InventoryService inventory;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> tokens = new HashMap<>();
    private long lauraId;
    private long tableId;
    private long wineId;

    @BeforeEach
    void setUp() throws Exception {
        long adminId = users.create("admin", "Administrador", "admin123", Role.ADMIN).getId();
        lauraId = users.create("laura", "Laura Gómez", "laura123", Role.WAITER).getId();
        users.create("jorge", "Jorge Ruiz", "jorge123", Role.KITCHEN);
        for (String username : List.of("admin", "laura", "jorge")) {
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
    @DisplayName("CA1 con un ticket válido me conecto al stream; cualquier rol autenticado puede pedirlo")
    void ca1ValidTicketOpensStream() throws Exception {
        for (String username : List.of("admin", "laura", "jorge")) {
            MvcResult result = openStream(ticketOf(username));
            assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
            assertThat(result.getResponse().getContentAsString()).contains(":connected");
        }
    }

    @Test
    @DisplayName("CA1 un ticket vencido (más de 60 s) → 401")
    void ca1ExpiredTicketIs401() throws Exception {
        String ticket = ticketOf("laura");
        clock.fixAt(NOW.plus(StreamTicketService.TTL).plusSeconds(1));
        mockMvc.perform(get(STREAM).param("ticket", ticket).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CA1 un ticket reutilizado → 401; uno inexistente o ausente → 401")
    void ca1ReusedOrUnknownTicketIs401() throws Exception {
        String ticket = ticketOf("laura");
        openStream(ticket);
        mockMvc.perform(get(STREAM).param("ticket", ticket).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get(STREAM).param("ticket", "no-existe").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(STREAM).accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CA1 pedir el ticket sin JWT → 401")
    void ca1TicketWithoutJwtIs401() throws Exception {
        mockMvc.perform(post(TICKET)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CA1 el ticket de un usuario que se desactivó → 401")
    void ca1TicketOfDeactivatedUserIs401() throws Exception {
        String ticket = ticketOf("laura");
        jdbcTemplate.update("UPDATE app_user SET active = false WHERE id = ?", lauraId);
        mockMvc.perform(get(STREAM).param("ticket", ticket).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CA3 con rollback no se emite order-changed; con commit sí")
    void ca3EmitsOnlyAfterCommit() throws Exception {
        MvcResult stream = openStream(ticketOf("laura"));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            publisher.publishEvent(new OrderChanged(77L, tableId, OrderStatus.PENDING));
            status.setRollbackOnly();
        });
        // Un evento posterior confirmado prueba que el canal está vivo y que el revertido nunca salió.
        tx.executeWithoutResult(status -> publisher.publishEvent(new OrderChanged(88L, tableId, OrderStatus.READY)));

        await().atMost(Duration.ofSeconds(5))
                .until(() -> stream.getResponse().getContentAsString().contains("\"orderId\":88"));
        String body = stream.getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"orderId\":77");
        assertThat(body).contains("event:order-changed").contains("\"status\":\"READY\"")
                .contains("\"tableId\":" + tableId);
    }

    @Test
    @DisplayName("CA3 un pedido que falla (409) no emite; crear, iniciar y marcar listo sí emiten order-changed")
    void ca3OrderLifecycleEmitsAfterCommit() throws Exception {
        MvcResult stream = openStream(ticketOf("laura"));
        long id = createOrder();
        as("jorge", post("/api/v1/orders/" + id + "/ready"), null).andExpect(status().isConflict());
        as("jorge", post("/api/v1/orders/" + id + "/start"), null).andExpect(status().isOk());
        as("jorge", post("/api/v1/orders/" + id + "/ready"), null).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(5))
                .until(() -> stream.getResponse().getContentAsString().contains("\"status\":\"READY\""));
        String body = stream.getResponse().getContentAsString();
        assertThat(body).contains("\"orderId\":" + id).contains("\"status\":\"PENDING\"")
                .contains("\"status\":\"IN_PREPARATION\"");
        // El 409 de "ready" sobre PENDING no emitió nada: solo PENDING, IN_PREPARATION y READY (3 eventos).
        assertThat(body.split("event:order-changed", -1)).hasSize(4);
    }

    @Test
    @DisplayName("CA3 cancelar un pedido emite order-changed con CANCELLED")
    void ca3CancelEmits() throws Exception {
        long id = createOrder();
        MvcResult stream = openStream(ticketOf("laura"));
        as("admin", post("/api/v1/orders/" + id + "/cancel"), Map.of("reason", "Cliente se fue"))
                .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(5))
                .until(() -> stream.getResponse().getContentAsString().contains("\"status\":\"CANCELLED\""));
    }

    private MvcResult openStream(String ticket) throws Exception {
        return mockMvc.perform(get(STREAM).param("ticket", ticket).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
    }

    private String ticketOf(String username) throws Exception {
        String response = as(username, post(TICKET), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("ticket").asString();
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
