package co.lacava.gestion.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import co.lacava.gestion.inventory.application.InventoryService;
import co.lacava.gestion.support.IntegrationTest;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.Role;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** LCV-HU-10: crear pedido (backend), comprobante de mesa (D37), cuenta de la mesa y meseros (D38). */
class OrderIntegrationTest extends IntegrationTest {

    private static final String ORDERS = "/api/v1/orders";
    private static final String TABLES = "/api/v1/tables";
    private static final String RESOLVE = TABLES + "/resolve";
    private static final String SCAN_AGAIN = "Escanea de nuevo el QR de la mesa.";
    /** 23:00 del 9 de marzo en Bogotá: "hoy" es 2026-03-09 para el negocio. */
    private static final Instant NOW = Instant.parse("2026-03-10T04:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-03-09");

    @Autowired
    private UserService users;

    @Autowired
    private InventoryService inventory;

    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> tokens = new HashMap<>();
    private long adminId;
    private long camilaId;
    private long lauraId;
    private long andresId;
    private long jorgeId;

    @BeforeEach
    void createUsers() throws Exception {
        adminId = users.create("admin", "Administrador", "admin123", Role.ADMIN).getId();
        camilaId = users.create("camila", "Camila Torres", "camila123", Role.CASHIER).getId();
        lauraId = users.create("laura", "Laura Gómez", "laura123", Role.WAITER).getId();
        andresId = users.create("andres", "Andrés Rojas", "andres123", Role.WAITER).getId();
        jorgeId = users.create("jorge", "Jorge Ruiz", "jorge123", Role.KITCHEN).getId();
        for (String username : List.of("admin", "camila", "laura", "andres", "jorge")) {
            tokens.put(username, tokenOf(username));
        }
        // Los JWT se emiten con el reloj real; el comprobante de mesa usa el reloj fijado.
        clock.fixAt(NOW);
    }

    // ---------- D37: resolve entrega el comprobante de mesa ----------

    @Test
    @DisplayName("D37 resolve (por QR o por número + PIN) devuelve tablePass LCP.<mesa>.<usuario>.<exp>.<firma> y passExpiresAt a 4 h")
    void resolveReturnsTablePass() throws Exception {
        long tableId = createTable(4);
        Instant expires = NOW.plus(Duration.ofHours(4));

        as("laura", post(RESOLVE), Map.of("code", codeOf(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tablePass").value(org.hamcrest.Matchers.startsWith(
                        "LCP." + tableId + "." + lauraId + "." + expires.getEpochSecond() + ".")))
                .andExpect(jsonPath("$.passExpiresAt").value(expires.toString()));
        as("camila", post(RESOLVE), Map.of("number", 4, "pin", pinOf(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tablePass").value(org.hamcrest.Matchers.startsWith(
                        "LCP." + tableId + "." + camilaId + ".")));
    }

    // ---------- CA4: quién crea y a quién queda asignado ----------

    @Test
    @DisplayName("CA4 el mesero crea con el tablePass de resolve: 201 PENDING, queda asignado y se ignora el waiterId del cuerpo")
    void ca4WaiterCreatesAndIsAssigned() throws Exception {
        long tableId = createTable(3);
        long wine = product("Copa de vino tinto", 18000);
        receive(wine, 10, TODAY.plusDays(30));

        Map<String, Object> body = order(tableId, List.of(item(wine, 2, null)), null, passOf("laura", 3));
        body.put("waiterId", andresId);
        String response = as("laura", post(ORDERS), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.tableId").value(tableId))
                .andExpect(jsonPath("$.tableNumber").value(3))
                .andExpect(jsonPath("$.waiterId").value(lauraId))
                .andExpect(jsonPath("$.waiterName").value("Laura Gómez"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value(NOW.toString()))
                .andReturn().getResponse().getContentAsString();
        long orderId = json.readTree(response).get("id").asLong();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT waiter_id, created_by, status, version FROM customer_order WHERE id = ?", orderId);
        assertThat(((Number) row.get("waiter_id")).longValue()).isEqualTo(lauraId);
        assertThat(((Number) row.get("created_by")).longValue()).isEqualTo(lauraId);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("version")).longValue()).isZero();
    }

    @Test
    @DisplayName("CA4 mesero sin tableCode, con pass de otra mesa, de otro mesero, alterado o mal formado → 403 \"Escanea de nuevo el QR de la mesa.\"")
    void ca4WaiterWithoutValidPassIsForbidden() throws Exception {
        long tableId = createTable(3);
        createTable(5);
        long wine = product("Copa de vino tinto", 18000);
        receive(wine, 10, TODAY.plusDays(30));
        String otherTable = passOf("laura", 5);
        String otherWaiter = passOf("andres", 3);
        String own = passOf("laura", 3);
        String tampered = own.substring(0, own.length() - 1) + (own.endsWith("A") ? "B" : "A");
        // Mismo pass con otro vencimiento: la firma ya no coincide.
        String[] parts = own.split("\\.");
        String longerExpiry = String.join(".", parts[0], parts[1], parts[2],
                Long.toString(Long.parseLong(parts[3]) + 3600), parts[4]);

        List<String> invalid = new ArrayList<>(List.of(otherTable, otherWaiter, tampered, longerExpiry,
                "LACAVA:MESA:3:" + tokenOf(3), "no es un pass", ""));
        invalid.add(null);
        for (String code : invalid) {
            as("laura", post(ORDERS), order(tableId, List.of(item(wine, 1, null)), null, code))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value(SCAN_AGAIN));
        }
        // Mesa inexistente: el mismo 403, sin revelar si existe.
        as("laura", post(ORDERS), order(999L, List.of(item(wine, 1, null)), null, own))
                .andExpect(status().isForbidden());
        assertNothingSaved();
        assertThat(inventory.available(wine)).isEqualTo(10);
    }

    @Test
    @DisplayName("CA4 el pass vencido (más de 4 h, Clock), tras regenerar el QR o con la mesa inactiva → 403")
    void ca4ExpiredRotatedOrInactiveTableIsForbidden() throws Exception {
        long tableId = createTable(3);
        long wine = product("Copa de vino tinto", 18000);
        receive(wine, 10, TODAY.plusDays(30));
        String pass = passOf("laura", 3);

        clock.fixAt(NOW.plus(Duration.ofHours(4)).plusSeconds(1));
        as("laura", post(ORDERS), order(tableId, List.of(item(wine, 1, null)), null, pass))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(SCAN_AGAIN));

        clock.fixAt(NOW.plus(Duration.ofHours(4)).minusSeconds(1));
        as("laura", post(ORDERS), order(tableId, List.of(item(wine, 1, null)), null, pass))
                .andExpect(status().isCreated());

        clock.fixAt(NOW.plusSeconds(60));
        String beforeRotation = passOf("laura", 3);
        as("admin", post(TABLES + "/" + tableId + "/rotate-token"), null).andExpect(status().isOk());
        as("laura", post(ORDERS), order(tableId, List.of(item(wine, 1, null)), null, beforeRotation))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(SCAN_AGAIN));

        String afterRotation = passOf("laura", 3);
        as("admin", patch(TABLES + "/" + tableId + "/active"), Map.of("active", false)).andExpect(status().isOk());
        as("laura", post(ORDERS), order(tableId, List.of(item(wine, 1, null)), null, afterRotation))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(SCAN_AGAIN));

        assertThat(count("SELECT count(*) FROM customer_order")).isEqualTo(1);
        assertThat(inventory.available(wine)).isEqualTo(9);
    }

    @Test
    @DisplayName("CA4 el cajero y el administrador eligen mesa y mesero (sin tableCode): 201 con ese mesero y created_by = quien lo registra")
    void ca4CashierAndAdminChooseTableAndWaiter() throws Exception {
        long tableId = createTable(7);
        long wine = product("Copa de vino tinto", 18000);
        receive(wine, 10, TODAY.plusDays(30));

        long byCashier = createdId(as("camila", post(ORDERS),
                withWaiter(order(tableId, List.of(item(wine, 1, null)), null, null), andresId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waiterId").value(andresId))
                .andExpect(jsonPath("$.waiterName").value("Andrés Rojas"))
                .andExpect(jsonPath("$.tableNumber").value(7)));
        long byAdmin = createdId(as("admin", post(ORDERS),
                withWaiter(order(tableId, List.of(item(wine, 1, null)), null, null), lauraId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waiterId").value(lauraId)));

        assertThat(jdbcTemplate.queryForObject("SELECT created_by FROM customer_order WHERE id = ?", Long.class,
                byCashier)).isEqualTo(camilaId);
        assertThat(jdbcTemplate.queryForObject("SELECT created_by FROM customer_order WHERE id = ?", Long.class,
                byAdmin)).isEqualTo(adminId);
    }

    @Test
    @DisplayName("CA4 cajero/admin: sin waiterId, o un waiterId que no es un mesero activo (cajero, preparación, inactivo, inexistente) → 400 en waiterId")
    void ca4WaiterIdMustBeActiveWaiter() throws Exception {
        long tableId = createTable(7);
        long wine = product("Copa de vino tinto", 18000);
        receive(wine, 10, TODAY.plusDays(30));
        long inactive = users.create("pedro", "Pedro Inactivo", "pedro123", Role.WAITER).getId();
        jdbcTemplate.update("UPDATE app_user SET active = false WHERE id = ?", inactive);

        as("camila", post(ORDERS), order(tableId, List.of(item(wine, 1, null)), null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("waiterId"))
                .andExpect(jsonPath("$.errors[0].message").value("Selecciona el mesero."));
        for (long waiterId : new long[] { camilaId, jorgeId, adminId, inactive, 999L }) {
            as("admin", post(ORDERS), withWaiter(order(tableId, List.of(item(wine, 1, null)), null, null), waiterId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("waiterId"))
                    .andExpect(jsonPath("$.errors[0].message").value("El mesero no existe o no está activo."));
        }
        assertNothingSaved();
    }

    @Test
    @DisplayName("CA4 cajero/admin: mesa inexistente → 404; mesa inactiva → 422; nada se guarda")
    void ca4CashierTableMustExistAndBeActive() throws Exception {
        long tableId = createTable(7);
        long wine = product("Copa de vino tinto", 18000);
        receive(wine, 10, TODAY.plusDays(30));

        as("camila", post(ORDERS), withWaiter(order(999L, List.of(item(wine, 1, null)), null, null), lauraId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La mesa no existe."));
        as("admin", patch(TABLES + "/" + tableId + "/active"), Map.of("active", false)).andExpect(status().isOk());
        as("camila", post(ORDERS), withWaiter(order(tableId, List.of(item(wine, 1, null)), null, null), lauraId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("La mesa 7 está inactiva."));
        assertNothingSaved();
    }

    @Test
    @DisplayName("CA4 Preparación (KITCHEN) no puede crear pedidos: 403")
    void ca4KitchenForbidden() throws Exception {
        long tableId = createTable(7);
        long wine = product("Copa de vino tinto", 18000);
        receive(wine, 10, TODAY.plusDays(30));

        as("jorge", post(ORDERS), withWaiter(order(tableId, List.of(item(wine, 1, null)), null, null), lauraId))
                .andExpect(status().isForbidden());
        assertNothingSaved();
    }

    // ---------- CA5: consumo FEFO, order_item_lot y rollback ----------

    @Test
    @DisplayName("CA5 consume FEFO repartido entre lotes e ítems repetidos: order_item_lot por ítem, movimientos SALE con order_id y total")
    void ca5ConsumesFefoWithOrderItemLots() throws Exception {
        long tableId = createTable(2);
        long wine = product("Copa de vino tinto", 18000);
        long board = product("Tabla La Cava", 58000);
        long expired = inventory.loadSampleLot(wine, 4, TODAY.minusDays(1), null, NOW, adminId).getId();
        long lotLate = receive(wine, 10, TODAY.plusDays(20));
        long lotSoon = receive(wine, 3, TODAY.plusDays(2));
        long boardLot = receive(board, 5, TODAY.plusDays(3));

        String response = as("camila", post(ORDERS), withWaiter(order(tableId,
                List.of(item(wine, 2, "  Bien fría  "), item(board, 1, null), item(wine, 3, null)),
                "  Mesa de cumpleaños  ", null), lauraId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.note").value("Mesa de cumpleaños"))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[*].productId").value(contains((int) wine, (int) board, (int) wine)))
                .andExpect(jsonPath("$.items[0].productName").value("Copa de vino tinto"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(18000.0))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].note").value("Bien fría"))
                .andExpect(jsonPath("$.items[0].subtotal").value(36000.0))
                .andExpect(jsonPath("$.items[1].subtotal").value(58000.0))
                .andExpect(jsonPath("$.items[2].note").doesNotExist())
                .andExpect(jsonPath("$.items[2].subtotal").value(54000.0))
                .andExpect(jsonPath("$.total").value(148000.0))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = json.readTree(response);
        long orderId = created.get("id").asLong();
        long firstWine = created.get("items").get(0).get("id").asLong();
        long boardItem = created.get("items").get(1).get("id").asLong();
        long secondWine = created.get("items").get(2).get("id").asLong();

        // FEFO: 3 del lote que vence antes y 2 del siguiente; el vencido no se toca.
        assertThat(availableOf(lotSoon)).isZero();
        assertThat(availableOf(lotLate)).isEqualTo(8);
        assertThat(availableOf(expired)).isEqualTo(4);
        assertThat(availableOf(boardLot)).isEqualTo(4);
        // Primer ítem de vino: 2 del lote próximo; segundo: 1 de ese lote y 2 del siguiente.
        assertThat(itemLots(firstWine)).containsExactly(Map.of(lotSoon, 2));
        assertThat(itemLots(secondWine)).containsExactly(Map.of(lotSoon, 1), Map.of(lotLate, 2));
        assertThat(itemLots(boardItem)).containsExactly(Map.of(boardLot, 1));
        List<Map<String, Object>> sales = jdbcTemplate.queryForList(
                "SELECT lot_id, quantity, order_id, user_id, note FROM stock_movement WHERE type = 'SALE' ORDER BY id");
        assertThat(sales).hasSize(3);
        assertThat(sales).allSatisfy(m -> {
            assertThat(((Number) m.get("order_id")).longValue()).isEqualTo(orderId);
            assertThat(((Number) m.get("user_id")).longValue()).isEqualTo(camilaId);
            assertThat(m.get("note")).isNull();
        });
        // Orden ascendente de productId (D33): primero el vino (creado antes), luego la tabla.
        assertThat(sales).extracting(m -> ((Number) m.get("lot_id")).longValue(), m -> m.get("quantity"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple(lotSoon, -3),
                        org.assertj.core.groups.Tuple.tuple(lotLate, -2),
                        org.assertj.core.groups.Tuple.tuple(boardLot, -1));
    }

    @Test
    @DisplayName("CA2 CA5 cantidad mayor que la vigente → 422 \"No hay suficiente Copa de vino tinto: disponibles 3.\" con productId y available; nada se guarda")
    void ca5NotEnoughStockSavesNothing() throws Exception {
        long tableId = createTable(2);
        long board = product("Tabla La Cava", 58000);
        long wine = product("Copa de vino tinto", 18000);
        long boardLot = receive(board, 5, TODAY.plusDays(3));
        long wineLot = receive(wine, 3, TODAY.plusDays(3));

        as("camila", post(ORDERS), withWaiter(order(tableId,
                List.of(item(board, 1, null), item(wine, 2, null), item(wine, 2, null)), null, null), lauraId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("No hay suficiente Copa de vino tinto: disponibles 3."))
                .andExpect(jsonPath("$.productId").value(wine))
                .andExpect(jsonPath("$.productName").value("Copa de vino tinto"))
                .andExpect(jsonPath("$.available").value(3));

        assertNothingSaved();
        assertThat(availableOf(boardLot)).isEqualTo(5);
        assertThat(availableOf(wineLot)).isEqualTo(3);
    }

    @Test
    @DisplayName("CA2 CA5 producto retirado de la carta, fuera de temporada o agotado → 422 con su motivo; producto inexistente → 404")
    void ca5UnavailableProducts() throws Exception {
        long tableId = createTable(2);
        long retired = product("Tabla para dos", 42000);
        long sangria = product("Sangría de verano", 24000);
        long empty = product("Tabla vegetariana", 45000);
        receive(retired, 5, TODAY.plusDays(3));
        receive(sangria, 6, TODAY.plusDays(30));
        jdbcTemplate.update("UPDATE product SET active = false WHERE id = ?", retired);
        jdbcTemplate.update("UPDATE product SET season_name = 'Verano', season_until = ? WHERE id = ?",
                TODAY.minusDays(1), sangria);

        as("camila", post(ORDERS), withWaiter(order(tableId, List.of(item(retired, 1, null)), null, null), lauraId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Tabla para dos no está disponible: Retirado de la carta."))
                .andExpect(jsonPath("$.productId").value(retired))
                .andExpect(jsonPath("$.available").value(5))
                .andExpect(jsonPath("$.reason").value("Retirado de la carta"));
        as("camila", post(ORDERS), withWaiter(order(tableId, List.of(item(sangria, 1, null)), null, null), lauraId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Sangría de verano no está disponible: Fuera de temporada."))
                .andExpect(jsonPath("$.reason").value("Fuera de temporada"));
        as("camila", post(ORDERS), withWaiter(order(tableId, List.of(item(empty, 1, null)), null, null), lauraId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("No hay suficiente Tabla vegetariana: disponibles 0."))
                .andExpect(jsonPath("$.available").value(0));
        as("camila", post(ORDERS), withWaiter(order(tableId, List.of(item(999L, 1, null)), null, null), lauraId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El producto no existe."));
        assertNothingSaved();
    }

    @Test
    @DisplayName("CA5 dos pedidos simultáneos que solo alcanzan para uno: uno 201 y otro 422; la existencia nunca queda negativa")
    void ca5ConcurrentOrdersOnlyOneWins() throws Exception {
        long tableA = createTable(1);
        long tableB = createTable(2);
        long wine = product("Copa de vino tinto", 18000);
        long lot = receive(wine, 5, TODAY.plusDays(5));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (long tableId : new long[] { tableA, tableB }) {
                results.add(pool.submit(() -> {
                    start.await();
                    return as("camila", post(ORDERS),
                            withWaiter(order(tableId, List.of(item(wine, 4, null)), null, null), lauraId))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 422);
        } finally {
            pool.shutdownNow();
        }

        assertThat(availableOf(lot)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM customer_order")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM order_item")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM order_item_lot")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM stock_movement WHERE type = 'SALE'")).isEqualTo(1);
    }

    // ---------- CA3: observaciones y validación ----------

    @Test
    @DisplayName("CA3 observación por ítem de 80 y general de 140 caracteres se guardan; 81 / 141 → 400 en items[i].note / note")
    void ca3NotesLimits() throws Exception {
        long tableId = createTable(2);
        long wine = product("Copa de vino tinto", 18000);
        receive(wine, 10, TODAY.plusDays(5));

        as("camila", post(ORDERS), withWaiter(order(tableId,
                List.of(item(wine, 1, "a".repeat(80))), "b".repeat(140), null), lauraId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].note").value("a".repeat(80)))
                .andExpect(jsonPath("$.note").value("b".repeat(140)));

        as("camila", post(ORDERS), withWaiter(order(tableId,
                List.of(item(wine, 1, null), item(wine, 1, "a".repeat(81))), "b".repeat(141), null), lauraId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("items[1].note", "note")));
        assertThat(count("SELECT count(*) FROM customer_order")).isEqualTo(1);
        assertThat(inventory.available(wine)).isEqualTo(9);
    }

    @Test
    @DisplayName("Validación: sin ítems, sin mesa, sin producto o con cantidad 0 / negativa → 400 errors[]; decimal → 400 en el campo")
    void validationErrors() throws Exception {
        long tableId = createTable(2);
        long wine = product("Copa de vino tinto", 18000);
        receive(wine, 10, TODAY.plusDays(5));

        as("camila", post(ORDERS), withWaiter(order(tableId, List.of(), null, null), lauraId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"));
        as("camila", post(ORDERS), withWaiter(order(null, List.of(item(null, 0, null), item(wine, -1, null)),
                null, null), lauraId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("tableId", "items[0].productId",
                        "items[0].quantity", "items[1].quantity")));
        mockMvc.perform(post(ORDERS).header("Authorization", "Bearer " + tokens.get("camila"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":" + tableId + ",\"waiterId\":" + lauraId
                                + ",\"items\":[{\"productId\":" + wine + ",\"quantity\":1.5}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"));
        assertNothingSaved();
    }

    @Test
    @DisplayName("Validación: una cantidad mayor que 999 por ítem → 400 en items[i].quantity; cantidades enormes del mismo producto no desbordan")
    void quantityUpperBound() throws Exception {
        long tableId = createTable(2);
        long wine = product("Copa de vino tinto", 18000);
        receive(wine, 10, TODAY.plusDays(5));

        as("camila", post(ORDERS), withWaiter(order(tableId,
                List.of(item(wine, 1, null), item(wine, Integer.MAX_VALUE, null), item(wine, Integer.MAX_VALUE, null)),
                null, null), lauraId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("items[1].quantity", "items[2].quantity")))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("La cantidad es demasiado grande (máximo 999 por producto)."));
        as("camila", post(ORDERS), withWaiter(order(tableId,
                List.of(item(wine, 999, null), item(wine, 999, null)), null, null), lauraId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.available").value(10));
        assertNothingSaved();
    }

    // ---------- Cuenta de la mesa y consulta ----------

    @Test
    @DisplayName("Cuenta: resolve y GET /tables/{id}/account muestran los pedidos abiertos con ítems y total; los cancelados no cuentan")
    void accountShowsOpenOrders() throws Exception {
        long tableId = createTable(4);
        createTable(5);
        long wine = product("Copa de vino tinto", 18000);
        long board = product("Tabla La Cava", 58000);
        receive(wine, 10, TODAY.plusDays(5));
        receive(board, 10, TODAY.plusDays(5));
        long first = createdId(as("laura", post(ORDERS),
                order(tableId, List.of(item(wine, 2, null)), null, passOf("laura", 4))));
        long second = createdId(as("camila", post(ORDERS),
                withWaiter(order(tableId, List.of(item(board, 1, null)), null, null), andresId)));
        long cancelled = createdId(as("camila", post(ORDERS),
                withWaiter(order(tableId, List.of(item(board, 1, null)), null, null), andresId)));
        jdbcTemplate.update("UPDATE customer_order SET status = 'CANCELLED', cancelled_at = now(), "
                + "cancel_reason = 'Prueba' WHERE id = ?", cancelled);

        as("andres", post(RESOLVE), Map.of("code", codeOf(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.orders[*].id").value(contains((int) first, (int) second)))
                .andExpect(jsonPath("$.account.orders[0].items[0].productName").value("Copa de vino tinto"))
                .andExpect(jsonPath("$.account.orders[0].total").value(36000.0))
                .andExpect(jsonPath("$.account.orders[1].waiterName").value("Andrés Rojas"))
                .andExpect(jsonPath("$.account.total").value(94000.0));
        as("admin", get(TABLES + "/" + tableId + "/account"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.orders", hasSize(2)))
                .andExpect(jsonPath("$.account.total").value(94000.0))
                .andExpect(jsonPath("$.tablePass").doesNotExist());
        as("camila", post(RESOLVE), Map.of("code", codeOf(5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.orders", hasSize(0)))
                .andExpect(jsonPath("$.account.total").value(0));
    }

    @Test
    @DisplayName("GET /orders/{id}: el mesero ve los suyos y uno ajeno da 404; cajero, admin y preparación lo ven; inexistente 404")
    void getOrder() throws Exception {
        long tableId = createTable(4);
        long wine = product("Copa de vino tinto", 18000);
        receive(wine, 10, TODAY.plusDays(5));
        long orderId = createdId(as("camila", post(ORDERS),
                withWaiter(order(tableId, List.of(item(wine, 2, null)), null, null), lauraId)));

        for (String user : List.of("laura", "camila", "admin", "jorge")) {
            as(user, get(ORDERS + "/" + orderId), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(orderId))
                    .andExpect(jsonPath("$.waiterId").value(lauraId))
                    .andExpect(jsonPath("$.total").value(36000.0));
        }
        as("andres", get(ORDERS + "/" + orderId), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El pedido no existe."));
        as("admin", get(ORDERS + "/999"), null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /users/waiters (A, C): meseros activos por nombre; mesero y preparación → 403")
    void waitersList() throws Exception {
        long inactive = users.create("beto", "Alberto Inactivo", "beto1234", Role.WAITER).getId();
        jdbcTemplate.update("UPDATE app_user SET active = false WHERE id = ?", inactive);

        for (String user : List.of("admin", "camila")) {
            as(user, get("/api/v1/users/waiters"), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].fullName").value(contains("Andrés Rojas", "Laura Gómez")))
                    .andExpect(jsonPath("$[*].id").value(contains((int) andresId, (int) lauraId)))
                    .andExpect(jsonPath("$[0].username").doesNotExist());
        }
        as("laura", get("/api/v1/users/waiters"), null).andExpect(status().isForbidden());
        as("jorge", get("/api/v1/users/waiters"), null).andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private void assertNothingSaved() {
        assertThat(count("SELECT count(*) FROM customer_order")).isZero();
        assertThat(count("SELECT count(*) FROM order_item")).isZero();
        assertThat(count("SELECT count(*) FROM order_item_lot")).isZero();
        assertThat(count("SELECT count(*) FROM stock_movement WHERE type = 'SALE'")).isZero();
    }

    private List<Map<Long, Integer>> itemLots(long orderItemId) {
        return jdbcTemplate.queryForList(
                "SELECT oil.lot_id, oil.quantity FROM order_item_lot oil JOIN stock_lot l ON l.id = oil.lot_id "
                        + "WHERE oil.order_item_id = ? ORDER BY l.expires_on, l.id", orderItemId).stream()
                .map(r -> Map.of(((Number) r.get("lot_id")).longValue(), ((Number) r.get("quantity")).intValue()))
                .toList();
    }

    private static Map<String, Object> order(Long tableId, List<Map<String, Object>> items, String note,
            String tableCode) {
        Map<String, Object> body = new HashMap<>();
        body.put("tableId", tableId);
        body.put("items", items);
        body.put("note", note);
        body.put("tableCode", tableCode);
        return body;
    }

    private static Map<String, Object> withWaiter(Map<String, Object> body, long waiterId) {
        body.put("waiterId", waiterId);
        return body;
    }

    private static Map<String, Object> item(Long productId, int quantity, String note) {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", productId);
        item.put("quantity", quantity);
        item.put("note", note);
        return item;
    }

    private long createdId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private String passOf(String username, int number) throws Exception {
        String body = as(username, post(RESOLVE), Map.of("code", codeOf(number)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("tablePass").asString();
    }

    private long createTable(int number) throws Exception {
        String body = as("admin", post(TABLES), Map.of("number", number))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private String tokenOf(int number) {
        return jdbcTemplate.queryForObject("SELECT qr_token FROM dining_table WHERE number = ?", String.class, number);
    }

    private String pinOf(int number) {
        return jdbcTemplate.queryForObject("SELECT pin FROM dining_table WHERE number = ?", String.class, number);
    }

    private String codeOf(int number) {
        return "LACAVA:MESA:" + number + ":" + tokenOf(number);
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
