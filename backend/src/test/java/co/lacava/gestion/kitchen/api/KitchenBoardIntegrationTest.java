package co.lacava.gestion.kitchen.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** LCV-HU-12: tablero de preparación y transiciones start/ready (D40). */
class KitchenBoardIntegrationTest extends IntegrationTest {

    private static final String BOARD = "/api/v1/kitchen/board";
    private static final String ORDERS = "/api/v1/orders";
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
    private long tableId;
    private long wineId;

    @BeforeEach
    void setUp() throws Exception {
        adminId = users.create("admin", "Administrador", "admin123", Role.ADMIN).getId();
        users.create("camila", "Camila Torres", "camila123", Role.CASHIER);
        lauraId = users.create("laura", "Laura Gómez", "laura123", Role.WAITER).getId();
        users.create("jorge", "Jorge Ruiz", "jorge123", Role.KITCHEN);
        for (String username : List.of("admin", "camila", "laura", "jorge")) {
            tokens.put(username, tokenOf(username));
        }
        clock.fixAt(NOW);
        tableId = idOf(as("admin", post("/api/v1/tables"), Map.of("number", 3)));
        Map<String, Object> product = new HashMap<>();
        product.put("name", "Copa de vino tinto");
        product.put("categoryName", "Carta");
        product.put("price", 18000);
        wineId = idOf(as("admin", post("/api/v1/products"), product));
        inventory.receiveLot(wineId, 100, TODAY.plusDays(30), null, adminId);
    }

    @Test
    @DisplayName("CA1 el tablero trae PENDING, IN_PREPARATION y READY con mesa, mesero, ítems, observaciones y hora; "
            + "excluye cancelados y entregados; ordena por estado y del más antiguo al más reciente")
    void ca1BoardIncludesOpenKitchenStatesInOrder() throws Exception {
        long pendingOld = createOrder("Sin hielo");
        clock.fixAt(NOW.plusSeconds(60));
        long ready = createOrder(null);
        clock.fixAt(NOW.plusSeconds(120));
        long pendingNew = createOrder(null);
        clock.fixAt(NOW.plusSeconds(180));
        long inPrep = createOrder(null);
        long cancelled = createOrder(null);
        long delivered = createOrder(null);
        as("jorge", post(ORDERS + "/" + ready + "/start"), null).andExpect(status().isOk());
        as("jorge", post(ORDERS + "/" + ready + "/ready"), null).andExpect(status().isOk());
        as("jorge", post(ORDERS + "/" + inPrep + "/start"), null).andExpect(status().isOk());
        as("camila", post(ORDERS + "/" + cancelled + "/cancel"), Map.of("reason", "Cliente se fue"))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE customer_order SET status = 'DELIVERED' WHERE id = ?", delivered);

        as("jorge", get(BOARD), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains((int) pendingOld, (int) pendingNew, (int) inPrep,
                        (int) ready)))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].tableNumber").value(3))
                .andExpect(jsonPath("$[0].waiterName").value("Laura Gómez"))
                .andExpect(jsonPath("$[0].note").value("Sin hielo"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-03-10T04:00:00Z"))
                .andExpect(jsonPath("$[0].items", hasSize(1)))
                .andExpect(jsonPath("$[0].items[0].productName").value("Copa de vino tinto"))
                .andExpect(jsonPath("$[0].items[0].note").value("Sin sal"))
                .andExpect(jsonPath("$[0].startedAt").value(nullValue()))
                .andExpect(jsonPath("$[3].readyAt").value("2026-03-10T04:03:00Z"));
        as("admin", get(BOARD), null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    @DisplayName("CA1 el tablero responde 403 al mesero y al cajero")
    void ca1BoardForbiddenForWaiterAndCashier() throws Exception {
        as("laura", get(BOARD), null).andExpect(status().isForbidden());
        as("camila", get(BOARD), null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CA2 start y ready cambian el estado, guardan startedAt/readyAt con el Clock y devuelven el pedido")
    void ca2StartAndReadyChangeStatusWithTimestamps() throws Exception {
        long id = createOrder(null);
        clock.fixAt(NOW.plusSeconds(300));
        as("jorge", post(ORDERS + "/" + id + "/start"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.status").value("IN_PREPARATION"))
                .andExpect(jsonPath("$.startedAt").value("2026-03-10T04:05:00Z"))
                .andExpect(jsonPath("$.readyAt").value(nullValue()));
        clock.fixAt(NOW.plusSeconds(900));
        as("admin", post(ORDERS + "/" + id + "/ready"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.startedAt").value("2026-03-10T04:05:00Z"))
                .andExpect(jsonPath("$.readyAt").value("2026-03-10T04:15:00Z"));
    }

    @Test
    @DisplayName("CA2 transiciones inválidas y doble start responden 409; inexistente 404")
    void ca2InvalidTransitionsAndMissingOrder() throws Exception {
        long id = createOrder(null);
        as("jorge", post(ORDERS + "/" + id + "/ready"), null).andExpect(status().isConflict());
        as("jorge", post(ORDERS + "/" + id + "/start"), null).andExpect(status().isOk());
        as("jorge", post(ORDERS + "/" + id + "/start"), null).andExpect(status().isConflict());
        as("jorge", post(ORDERS + "/" + id + "/ready"), null).andExpect(status().isOk());
        as("jorge", post(ORDERS + "/" + id + "/ready"), null).andExpect(status().isConflict());
        as("jorge", post(ORDERS + "/" + id + "/start"), null).andExpect(status().isConflict());
        as("jorge", post(ORDERS + "/999999/start"), null).andExpect(status().isNotFound());
        as("jorge", post(ORDERS + "/999999/ready"), null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CA2 start y ready responden 403 al mesero y al cajero y no cambian el pedido")
    void ca2StartAndReadyForbiddenForWaiterAndCashier() throws Exception {
        long id = createOrder(null);
        as("laura", post(ORDERS + "/" + id + "/start"), null).andExpect(status().isForbidden());
        as("camila", post(ORDERS + "/" + id + "/start"), null).andExpect(status().isForbidden());
        as("camila", post(ORDERS + "/" + id + "/ready"), null).andExpect(status().isForbidden());
        as("jorge", get(BOARD), null).andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    // ---------- helpers ----------

    private long createOrder(String note) throws Exception {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", wineId);
        item.put("quantity", 1);
        item.put("note", "Sin sal");
        Map<String, Object> body = new HashMap<>();
        body.put("tableId", tableId);
        body.put("waiterId", lauraId);
        body.put("items", List.of(item));
        body.put("note", note);
        return idOf(as("admin", post(ORDERS), body));
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
