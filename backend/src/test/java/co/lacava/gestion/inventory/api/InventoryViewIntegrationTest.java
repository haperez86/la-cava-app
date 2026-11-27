package co.lacava.gestion.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import co.lacava.gestion.inventory.application.InventoryService;
import co.lacava.gestion.inventory.domain.MovementType;
import co.lacava.gestion.menu.application.ProductData;
import co.lacava.gestion.menu.application.ProductService;
import co.lacava.gestion.support.IntegrationTest;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.Role;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** LCV-HU-06: vista de inventario y alertas (backend, D34). */
class InventoryViewIntegrationTest extends IntegrationTest {

    private static final String INVENTORY = "/api/v1/inventory";
    private static final String SUMMARY = "/api/v1/inventory/summary";
    private static final String MOVEMENTS = "/api/v1/inventory/movements";
    /** 23:00 del 9 de marzo en Bogotá (ya 10 de marzo en UTC): "hoy" es 2026-03-09 para el negocio. */
    private static final Instant NOW = Instant.parse("2026-03-10T04:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-03-09");

    @Autowired
    private UserService users;

    @Autowired
    private ProductService productService;

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

    /**
     * Catálogo con un caso de cada estado. Activos: Agotado (OUT), Bajo (LOW), Hoy (EXPIRING, vence hoy),
     * Mañana (EXPIRING y con vencidos), Disponible (AVAILABLE, vence en 4 días), Fuera (OUT_OF_SEASON sin lotes),
     * Temporada (LOW y además vence en 2 días; categoría Tablas) y Vencido (OUT con vencidos; categoría Tablas).
     * Inactivo: retirado, con lote vencido y sin existencia.
     */
    @Nested
    class Catalog {

        private long tablas;
        private long vinos;

        @BeforeEach
        void createCatalog() {
            product("Agotado", "Vinos", 2, null, null);
            lot(product("Bajo", "Vinos", 5, null, null), 3, TODAY.plusDays(30));
            long hoy = product("Hoy", "Vinos", 0, null, null);
            lot(hoy, 10, TODAY);
            lot(hoy, 4, TODAY.plusDays(10));
            long manana = product("Mañana", "Vinos", 1, null, null);
            lot(manana, 6, TODAY.plusDays(1));
            lot(manana, 2, TODAY.minusDays(1));
            lot(product("Disponible", "Vinos", 2, null, null), 20, TODAY.plusDays(4));
            product("Fuera", "Vinos", 0, "Navidad", TODAY.minusDays(1));
            lot(product("Temporada", "Tablas", 10, "Verano", TODAY.plusDays(30)), 8, TODAY.plusDays(2));
            lot(product("Vencido", "Tablas", 0, null, null), 7, TODAY.minusDays(3));
            long inactive = product("Inactivo", "Vinos", 3, "Halloween", TODAY.plusDays(10));
            lot(inactive, 5, TODAY.minusDays(2));
            productService.setActive(inactive, false);
            tablas = productService.findByName("Temporada").orElseThrow().categoryId();
            vinos = productService.findByName("Agotado").orElseThrow().categoryId();
        }

        @Test
        @DisplayName("CA1 los contadores cuentan agotados, stock bajo, por vencer y unidades vencidas de los activos")
        void ca1Summary() throws Exception {
            as(Role.ADMIN, get(SUMMARY))
                    .andExpect(status().isOk())
                    // Agotado y Vencido; Fuera (fuera de temporada) e Inactivo no cuentan.
                    .andExpect(jsonPath("$.outOfStock").value(2))
                    .andExpect(jsonPath("$.lowStock").value(2))
                    .andExpect(jsonPath("$.expiring").value(3))
                    // 2 de Mañana + 7 de Vencido; los 5 del inactivo no cuentan.
                    .andExpect(jsonPath("$.expiredUnits").value(9))
                    .andExpect(jsonPath("$.seasons.length()").value(2))
                    .andExpect(jsonPath("$.seasons[0]").value("Navidad"))
                    .andExpect(jsonPath("$.seasons[1]").value("Verano"));
        }

        @Test
        @DisplayName("CA2 cada fila trae vigente, mínimo, próximo vencimiento, días, vencidas y estado por prioridad")
        void ca2Rows() throws Exception {
            JsonNode page = page(INVENTORY + "?size=50");
            assertThat(page.get("totalElements").asInt()).isEqualTo(8);
            assertThat(names(page)).containsExactly("Agotado", "Bajo", "Disponible", "Fuera", "Hoy", "Mañana",
                    "Temporada", "Vencido");

            assertRow(row(page, "Agotado"), 0, 2, null, null, 0, "OUT");
            assertRow(row(page, "Bajo"), 3, 5, TODAY.plusDays(30), 30, 0, "LOW");
            assertRow(row(page, "Hoy"), 14, 0, TODAY, 0, 0, "EXPIRING");
            assertRow(row(page, "Mañana"), 6, 1, TODAY.plusDays(1), 1, 2, "EXPIRING");
            assertRow(row(page, "Disponible"), 20, 2, TODAY.plusDays(4), 4, 0, "AVAILABLE");
            assertRow(row(page, "Fuera"), 0, 0, null, null, 0, "OUT_OF_SEASON");
            assertRow(row(page, "Temporada"), 8, 10, TODAY.plusDays(2), 2, 0, "LOW");
            assertRow(row(page, "Vencido"), 0, 0, null, null, 7, "OUT");

            JsonNode season = row(page, "Temporada");
            assertThat(season.get("seasonName").asString()).isEqualTo("Verano");
            assertThat(season.get("seasonUntil").asString()).isEqualTo(TODAY.plusDays(30).toString());
            assertThat(season.get("categoryName").asString()).isEqualTo("Tablas");
            assertThat(season.get("categoryId").asLong()).isEqualTo(tablas);
            assertThat(season.get("icon").asString()).isEqualTo("🍷");
            assertThat(season.get("productId").asLong())
                    .isEqualTo(productService.findByName("Temporada").orElseThrow().id());
            assertThat(row(page, "Mañana").get("hasExpired").asBoolean()).isTrue();
            assertThat(row(page, "Hoy").get("hasExpired").asBoolean()).isFalse();
            assertThat(row(page, "Agotado").get("seasonName").isNull()).isTrue();
        }

        @ParameterizedTest(name = "state={0} → {1}")
        @CsvSource({
                "OUT, 'Agotado,Vencido'",
                "LOW, 'Bajo,Temporada'",
                "EXPIRING, 'Hoy,Mañana,Temporada'",
                "HAS_EXPIRED, 'Mañana,Vencido'",
                "OUT_OF_SEASON, 'Fuera'",
                "AVAILABLE, 'Disponible'",
                "low, 'Bajo,Temporada'" })
        @DisplayName("CA3 cada filtro de estado devuelve su conjunto con totalElements correcto")
        void ca3StateFilter(String state, String expected) throws Exception {
            JsonNode page = page(INVENTORY + "?state=" + state);
            List<String> names = List.of(expected.split(","));
            assertThat(page.get("totalElements").asInt()).isEqualTo(names.size());
            assertThat(names(page)).containsExactlyElementsOf(names);
        }

        @Test
        @DisplayName("CA3 temporada exacta sin distinguir mayúsculas, texto con comodines escapados y categoría")
        void ca3SeasonTextAndCategory() throws Exception {
            assertThat(names(page(INVENTORY + "?season=verano"))).containsExactly("Temporada");
            assertThat(names(page(INVENTORY + "?season=NAVIDAD"))).containsExactly("Fuera");
            assertThat(page(INVENTORY + "?season=Nav").get("totalElements").asInt()).isZero();
            assertThat(page(INVENTORY + "?season=Halloween").get("totalElements").asInt()).isZero();
            assertThat(names(page(INVENTORY + "?q=  MA "))).containsExactly("Mañana");
            assertThat(page(INVENTORY + "?q=%25").get("totalElements").asInt()).isZero();
            assertThat(page(INVENTORY + "?q=_").get("totalElements").asInt()).isZero();
            assertThat(names(page(INVENTORY + "?categoryId=" + tablas))).containsExactly("Temporada", "Vencido");
        }

        @Test
        @DisplayName("CA3 los filtros se combinan y la paginación cuenta sobre el resultado filtrado")
        void ca3CombinedFiltersAndPaging() throws Exception {
            assertThat(names(page(INVENTORY + "?categoryId=" + tablas + "&state=OUT"))).containsExactly("Vencido");
            assertThat(names(page(INVENTORY + "?categoryId=" + vinos + "&state=EXPIRING")))
                    .containsExactly("Hoy", "Mañana");
            assertThat(names(page(INVENTORY + "?season=Verano&state=LOW"))).containsExactly("Temporada");
            assertThat(names(page(INVENTORY + "?season=Verano&state=OUT")).isEmpty()).isTrue();
            assertThat(names(page(INVENTORY + "?q=a&state=HAS_EXPIRED&categoryId=" + vinos)))
                    .containsExactly("Mañana");

            JsonNode first = page(INVENTORY + "?size=3");
            assertThat(first.get("totalElements").asInt()).isEqualTo(8);
            assertThat(first.get("totalPages").asInt()).isEqualTo(3);
            assertThat(names(first)).containsExactly("Agotado", "Bajo", "Disponible");
            JsonNode last = page(INVENTORY + "?size=2&page=2&state=EXPIRING");
            assertThat(last.get("totalElements").asInt()).isEqualTo(3);
            assertThat(names(last)).isEmpty();
            assertThat(names(page(INVENTORY + "?size=2&page=1&state=EXPIRING"))).containsExactly("Temporada");
        }

        @Test
        @DisplayName("CA3 se ordena por vigente o mínimo; otro campo de orden → 400 en sort")
        void ca3Sort() throws Exception {
            assertThat(names(page(INVENTORY + "?sort=available,desc&size=4")))
                    .containsExactly("Disponible", "Hoy", "Temporada", "Mañana");
            assertThat(names(page(INVENTORY + "?sort=minStock,desc&size=3")))
                    .containsExactly("Temporada", "Bajo", "Agotado");
            assertThat(names(page(INVENTORY + "?sort=name,desc&size=2"))).containsExactly("Vencido", "Temporada");
            as(Role.ADMIN, get(INVENTORY + "?sort=price"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("sort"));
        }

        @Test
        @DisplayName("CA3 un estado inválido → 400 en state")
        void ca3InvalidState() throws Exception {
            as(Role.ADMIN, get(INVENTORY + "?state=INACTIVE"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("state"))
                    .andExpect(jsonPath("$.errors[0].message").value(
                            "El estado debe ser AVAILABLE, LOW, OUT, EXPIRING, HAS_EXPIRED u OUT_OF_SEASON."));
        }
    }

    // ---------- CA4: movimientos recientes ----------

    @Test
    @DisplayName("CA4 los movimientos se filtran por producto y por fechas de Bogotá inclusivas, del más nuevo al más viejo")
    void ca4Movements() throws Exception {
        long wine = product("Copa de vino", "Vinos", 0, null, null);
        long cheese = product("Tabla de quesos", "Tablas", 0, null, null);
        // 23:59 del 7 de marzo en Bogotá.
        long m1 = movementAt("2026-03-08T04:59:00Z", wine);
        // 00:00 del 8 de marzo en Bogotá.
        long m2 = movementAt("2026-03-08T05:00:00Z", wine);
        // 23:59:59 del 8 de marzo en Bogotá (ya 9 de marzo en UTC).
        long m3 = movementAt("2026-03-09T04:59:59Z", cheese);
        // 00:00 del 9 de marzo en Bogotá: entrada y salida en el mismo instante (desempate por id desc).
        long m4 = movementAt("2026-03-09T05:00:00Z", wine);
        inventory.consume(wine, 1, MovementType.WASTE_OUT, "Botella rota", adminId, null);
        long m5 = jdbcTemplate.queryForObject("SELECT max(id) FROM stock_movement", Long.class);
        clock.fixAt(NOW);

        JsonNode all = page(MOVEMENTS);
        assertThat(all.get("totalElements").asInt()).isEqualTo(5);
        assertThat(ids(all)).containsExactly(m5, m4, m3, m2, m1);
        JsonNode out = all.get("content").get(0);
        assertThat(out.get("type").asString()).isEqualTo("WASTE_OUT");
        assertThat(out.get("quantity").asInt()).isEqualTo(-1);
        assertThat(out.get("note").asString()).isEqualTo("Botella rota");
        assertThat(out.get("productId").asLong()).isEqualTo(wine);
        assertThat(out.get("productName").asString()).isEqualTo("Copa de vino");
        assertThat(out.get("userName").asString()).isEqualTo("Administrador");
        assertThat(out.get("lotId").isNull()).isFalse();
        assertThat(out.get("orderId").isNull()).isTrue();
        assertThat(out.get("createdAt").asString()).isEqualTo("2026-03-09T05:00:00Z");
        assertThat(all.get("content").get(2).get("productName").asString()).isEqualTo("Tabla de quesos");

        assertThat(ids(page(MOVEMENTS + "?productId=" + wine))).containsExactly(m5, m4, m2, m1);
        assertThat(ids(page(MOVEMENTS + "?from=2026-03-08&to=2026-03-08"))).containsExactly(m3, m2);
        assertThat(ids(page(MOVEMENTS + "?from=2026-03-09"))).containsExactly(m5, m4);
        assertThat(ids(page(MOVEMENTS + "?to=2026-03-07"))).containsExactly(m1);
        assertThat(ids(page(MOVEMENTS + "?productId=" + wine + "&from=2026-03-08&to=2026-03-08")))
                .containsExactly(m2);
        JsonNode paged = page(MOVEMENTS + "?size=2&page=1");
        assertThat(paged.get("totalElements").asInt()).isEqualTo(5);
        assertThat(ids(paged)).containsExactly(m3, m2);
    }

    @Test
    @DisplayName("CA4 from posterior a to → 400 en from")
    void ca4InvalidRange() throws Exception {
        as(Role.ADMIN, get(MOVEMENTS + "?from=2026-03-09&to=2026-03-08"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
    }

    @ParameterizedTest(name = "{0} en {1} → 403")
    @CsvSource({
            "CASHIER, /api/v1/inventory", "WAITER, /api/v1/inventory", "KITCHEN, /api/v1/inventory",
            "CASHIER, /api/v1/inventory/summary", "WAITER, /api/v1/inventory/summary",
            "KITCHEN, /api/v1/inventory/summary",
            "CASHIER, /api/v1/inventory/movements", "WAITER, /api/v1/inventory/movements",
            "KITCHEN, /api/v1/inventory/movements" })
    @DisplayName("CA1-CA4 solo el administrador consulta la vista de inventario")
    void forbiddenForNonAdmin(Role role, String path) throws Exception {
        as(role, get(path)).andExpect(status().isForbidden());
    }

    // ---------- utilidades ----------

    private long movementAt(String instant, long productId) {
        clock.fixAt(Instant.parse(instant));
        inventory.loadSampleLot(productId, 5, TODAY.plusDays(30), null, Instant.parse(instant), adminId);
        return jdbcTemplate.queryForObject("SELECT max(id) FROM stock_movement", Long.class);
    }

    private long product(String name, String category, int minStock, String seasonName, LocalDate seasonUntil) {
        return productService.create(new ProductData(name, category, new BigDecimal("20000"), null, "🍷", minStock,
                seasonName, seasonUntil), null, adminId).id();
    }

    private void lot(long productId, int quantity, LocalDate expiresOn) {
        inventory.loadSampleLot(productId, quantity, expiresOn, null, NOW, adminId);
    }

    private static void assertRow(JsonNode row, int available, int minStock, LocalDate nextExpiresOn,
            Integer daysToExpiry, int expiredUnits, String state) {
        assertThat(row.get("available").asInt()).as("available").isEqualTo(available);
        assertThat(row.get("minStock").asInt()).as("minStock").isEqualTo(minStock);
        if (nextExpiresOn == null) {
            assertThat(row.get("nextExpiresOn").isNull()).as("nextExpiresOn").isTrue();
            assertThat(row.get("daysToExpiry").isNull()).as("daysToExpiry").isTrue();
        } else {
            assertThat(row.get("nextExpiresOn").asString()).as("nextExpiresOn").isEqualTo(nextExpiresOn.toString());
            assertThat(row.get("daysToExpiry").asInt()).as("daysToExpiry").isEqualTo(daysToExpiry);
        }
        assertThat(row.get("expiredUnits").asInt()).as("expiredUnits").isEqualTo(expiredUnits);
        assertThat(row.get("hasExpired").asBoolean()).as("hasExpired").isEqualTo(expiredUnits > 0);
        assertThat(row.get("state").asString()).as("state").isEqualTo(state);
    }

    private static JsonNode row(JsonNode page, String name) {
        for (JsonNode row : page.get("content")) {
            if (row.get("name").asString().equals(name)) {
                return row;
            }
        }
        throw new AssertionError("No está la fila " + name);
    }

    private static List<String> names(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("content").forEach(row -> names.add(row.get("name").asString()));
        return names;
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(row -> ids.add(row.get("id").asLong()));
        return ids;
    }

    private JsonNode page(String url) throws Exception {
        String body = as(Role.ADMIN, get(url)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return json.readTree(body);
    }

    private ResultActions as(Role role, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + tokens.get(role)));
    }

    private String tokenOf(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", username + "123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asString();
    }
}
