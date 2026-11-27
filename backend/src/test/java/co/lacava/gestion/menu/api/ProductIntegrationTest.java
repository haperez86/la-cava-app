package co.lacava.gestion.menu.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** LCV-HU-02: productos y categorías (backend). */
class ProductIntegrationTest extends IntegrationTest {

    private static final String PRODUCTS = "/api/v1/products";
    private static final String DUPLICATE = "Ya existe un producto con ese nombre.";
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

    // ---------- CA1: crear, editar, duplicado, categorías al vuelo ----------

    @Test
    @DisplayName("CA1 el administrador crea un producto y recibe 201 con el ProductResponse completo")
    void ca1CreatesProduct() throws Exception {
        as(Role.ADMIN, post(PRODUCTS), body("Tabla La Cava", "Tablas"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Tabla La Cava"))
                .andExpect(jsonPath("$.categoryId").isNumber())
                .andExpect(jsonPath("$.categoryName").value("Tablas"))
                .andExpect(jsonPath("$.price").value(58000.0))
                .andExpect(jsonPath("$.description").value("Quesos y charcutería"))
                .andExpect(jsonPath("$.icon").value("🧀"))
                .andExpect(jsonPath("$.minStock").value(4))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.seasonName").doesNotExist())
                .andExpect(jsonPath("$.available").value(0));

        assertThat(count("SELECT count(*) FROM product WHERE name = 'Tabla La Cava'")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA1 el administrador edita un producto y los cambios quedan guardados")
    void ca1EditsProduct() throws Exception {
        long id = createProduct(body("Tabla La Cava", "Tablas"));
        Map<String, Object> edit = body("Tabla La Cava Grande", "Tablas");
        edit.put("price", 64000);
        edit.put("minStock", 6);

        as(Role.ADMIN, put(PRODUCTS + "/" + id), edit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Tabla La Cava Grande"))
                .andExpect(jsonPath("$.price").value(64000.0))
                .andExpect(jsonPath("$.minStock").value(6));

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM product WHERE id = ?", String.class, id))
                .isEqualTo("Tabla La Cava Grande");
    }

    @ParameterizedTest
    @ValueSource(strings = { "Tabla La Cava", "tabla la cava", "  TABLA LA CAVA  " })
    @DisplayName("CA1 un nombre duplicado (sin distinguir mayúsculas) al crear da 409 y no crea otro producto")
    void ca1DuplicateNameOnCreate(String duplicate) throws Exception {
        createProduct(body("Tabla La Cava", "Tablas"));

        as(Role.ADMIN, post(PRODUCTS), body(duplicate, "Tablas"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(DUPLICATE));

        assertThat(count("SELECT count(*) FROM product")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA1 renombrar un producto con el nombre de otro da 409; conservar el propio nombre no")
    void ca1DuplicateNameOnEdit() throws Exception {
        createProduct(body("Tabla La Cava", "Tablas"));
        long id = createProduct(body("Tabla para dos", "Tablas"));

        as(Role.ADMIN, put(PRODUCTS + "/" + id), body("TABLA LA CAVA", "Tablas"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(DUPLICATE));
        as(Role.ADMIN, put(PRODUCTS + "/" + id), body("tabla para dos", "Tablas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("tabla para dos"));
    }

    @Test
    @DisplayName("CA1 la categoría nueva se crea al vuelo y se reutiliza sin distinguir mayúsculas ni espacios")
    void ca1CategoryCreatedOnTheFlyAndReused() throws Exception {
        long first = createProduct(body("Copa de vino tinto", "Vinos"));
        long second = createProduct(body("Vino botella", "  vinos "));

        assertThat(count("SELECT count(*) FROM category")).isEqualTo(1);
        Long firstCategory = jdbcTemplate.queryForObject("SELECT category_id FROM product WHERE id = ?", Long.class, first);
        Long secondCategory = jdbcTemplate.queryForObject("SELECT category_id FROM product WHERE id = ?", Long.class, second);
        assertThat(secondCategory).isEqualTo(firstCategory);

        as(Role.ADMIN, put(PRODUCTS + "/" + second), body("Vino botella", "Bodega"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Bodega"));
        assertThat(count("SELECT count(*) FROM category")).isEqualTo(2);
    }

    @Test
    @DisplayName("CA1 validación por campo: nombre, categoría, precio, descripción, ícono y stock mínimo")
    void ca1FieldValidation() throws Exception {
        Map<String, Object> invalid = new HashMap<>();
        invalid.put("name", "");
        invalid.put("categoryName", "x".repeat(41));
        invalid.put("price", 0);
        invalid.put("description", "x".repeat(121));
        invalid.put("icon", "x".repeat(17));
        invalid.put("minStock", -1);
        invalid.put("seasonName", "x".repeat(41));

        as(Role.ADMIN, post(PRODUCTS), invalid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Revisa los datos enviados."))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value(hasItem("El nombre es obligatorio.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'categoryName')].message")
                        .value(hasItem("La categoría admite máximo 40 caracteres.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'price')].message").value(hasItem("El precio debe ser mayor que 0.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'description')].message")
                        .value(hasItem("La descripción admite máximo 120 caracteres.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'icon')].message").value(hasItem("El ícono admite máximo 16 caracteres.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'minStock')].message")
                        .value(hasItem("El stock mínimo no puede ser negativo.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'seasonName')].message")
                        .value(hasItem("El nombre de la temporada admite máximo 40 caracteres.")));

        assertThat(count("SELECT count(*) FROM product")).isZero();
    }

    @Test
    @DisplayName("CA1 nombre de más de 60 caracteres, categoría vacía y precio faltante dan 400")
    void ca1MoreFieldValidation() throws Exception {
        Map<String, Object> invalid = new HashMap<>();
        invalid.put("name", "x".repeat(61));
        invalid.put("categoryName", " ");

        as(Role.ADMIN, post(PRODUCTS), invalid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message")
                        .value(hasItem("El nombre admite máximo 60 caracteres.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'categoryName')].message")
                        .value(hasItem("La categoría es obligatoria.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'price')].message").value(hasItem("El precio es obligatorio.")));
    }

    @Test
    @DisplayName("CA1 editar un producto inexistente da 404")
    void ca1EditMissingProduct() throws Exception {
        as(Role.ADMIN, put(PRODUCTS + "/999"), body("Fantasma", "Tablas"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El producto no existe."));
        as(Role.ADMIN, patch(PRODUCTS + "/999/active"), Map.of("active", false))
                .andExpect(status().isNotFound());
    }

    // ---------- CA2: temporada ----------

    @Test
    @DisplayName("CA2 con nombre de temporada y sin fecha \"disponible hasta\" da 400 al crear y al editar")
    void ca2SeasonRequiresUntil() throws Exception {
        Map<String, Object> seasonal = body("Vino caliente navideño", "Vinos");
        seasonal.put("seasonName", "Navidad");

        as(Role.ADMIN, post(PRODUCTS), seasonal)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("seasonUntil"))
                .andExpect(jsonPath("$.errors[0].message").value("Indica la fecha \"disponible hasta\" de la temporada."));
        assertThat(count("SELECT count(*) FROM product")).isZero();

        long id = createProduct(body("Vino caliente navideño", "Vinos"));
        as(Role.ADMIN, put(PRODUCTS + "/" + id), seasonal)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("seasonUntil"));
    }

    @Test
    @DisplayName("CA2 con nombre de temporada y fecha se guarda la temporada")
    void ca2SeasonWithUntil() throws Exception {
        Map<String, Object> seasonal = body("Vino caliente navideño", "Vinos");
        seasonal.put("seasonName", "Navidad");
        seasonal.put("seasonUntil", "2026-12-31");

        as(Role.ADMIN, post(PRODUCTS), seasonal)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seasonName").value("Navidad"))
                .andExpect(jsonPath("$.seasonUntil").value("2026-12-31"));
    }

    // ---------- CA3: activar / desactivar ----------

    @Test
    @DisplayName("CA3 el administrador retira un producto de la carta y lo vuelve a poner")
    void ca3DeactivateAndActivate() throws Exception {
        long id = createProduct(body("Agua", "Bebidas"));

        as(Role.ADMIN, patch(PRODUCTS + "/" + id + "/active"), Map.of("active", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        assertThat(jdbcTemplate.queryForObject("SELECT active FROM product WHERE id = ?", Boolean.class, id)).isFalse();

        as(Role.ADMIN, patch(PRODUCTS + "/" + id + "/active"), Map.of("active", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        assertThat(jdbcTemplate.queryForObject("SELECT active FROM product WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    @DisplayName("CA3 PATCH sin el campo active da 400")
    void ca3ActiveRequired() throws Exception {
        long id = createProduct(body("Agua", "Bebidas"));

        as(Role.ADMIN, patch(PRODUCTS + "/" + id + "/active"), Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("active"));
    }

    // ---------- CA4: lote inicial ----------

    @Test
    @DisplayName("CA4 con lote inicial se crean el stock_lot y el movimiento LOT_IN con el usuario de la sesión")
    void ca4InitialLotCreatesLotAndMovement() throws Exception {
        Map<String, Object> withLot = body("Tabla La Cava", "Tablas");
        withLot.put("initialLot", Map.of("quantity", 9, "expiresOn", TODAY.toString()));

        long id = json.readTree(as(Role.ADMIN, post(PRODUCTS), withLot)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(9))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        Map<String, Object> lot = jdbcTemplate.queryForMap("SELECT * FROM stock_lot WHERE product_id = ?", id);
        assertThat(lot.get("quantity_received")).isEqualTo(9);
        assertThat(lot.get("quantity_available")).isEqualTo(9);
        assertThat(lot.get("expires_on").toString()).isEqualTo(TODAY.toString());
        assertThat(((Number) lot.get("created_by")).longValue()).isEqualTo(adminId);
        Map<String, Object> movement = jdbcTemplate.queryForMap("SELECT * FROM stock_movement WHERE product_id = ?", id);
        assertThat(movement.get("type")).isEqualTo("LOT_IN");
        assertThat(movement.get("quantity")).isEqualTo(9);
        assertThat(((Number) movement.get("user_id")).longValue()).isEqualTo(adminId);
        assertThat(((Number) movement.get("lot_id")).longValue()).isEqualTo(((Number) lot.get("id")).longValue());
    }

    @Test
    @DisplayName("CA4 el lote inicial toma la temporada del producto")
    void ca4InitialLotTakesProductSeason() throws Exception {
        Map<String, Object> withLot = body("Vino caliente navideño", "Vinos");
        withLot.put("seasonName", "Navidad");
        withLot.put("seasonUntil", "2026-12-31");
        withLot.put("initialLot", Map.of("quantity", 15, "expiresOn", "2026-04-01"));

        as(Role.ADMIN, post(PRODUCTS), withLot).andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("SELECT season_name FROM stock_lot", String.class)).isEqualTo("Navidad");
    }

    @Test
    @DisplayName("CA4 cantidad sin vencimiento da 400 y no se crea el producto ni el lote")
    void ca4MissingExpiresOn() throws Exception {
        Map<String, Object> withLot = body("Tabla La Cava", "Tablas");
        withLot.put("initialLot", Map.of("quantity", 9));

        as(Role.ADMIN, post(PRODUCTS), withLot)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("initialLot.expiresOn"))
                .andExpect(jsonPath("$.errors[0].message").value("La fecha de vencimiento es obligatoria."));

        assertNothingCreated();
    }

    @Test
    @DisplayName("CA4 vencimiento anterior a hoy (según el Clock y la zona de Bogotá) da 400 y no crea nada")
    void ca4ExpiresOnBeforeToday() throws Exception {
        Map<String, Object> withLot = body("Tabla La Cava", "Tablas");
        withLot.put("initialLot", Map.of("quantity", 9, "expiresOn", TODAY.minusDays(1).toString()));

        as(Role.ADMIN, post(PRODUCTS), withLot)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("initialLot.expiresOn"))
                .andExpect(jsonPath("$.errors[0].message").value("La fecha de vencimiento no puede ser anterior a hoy."));

        assertNothingCreated();
    }

    @Test
    @DisplayName("CA4 cantidad del lote inicial no positiva da 400 y no crea nada")
    void ca4QuantityMustBePositive() throws Exception {
        Map<String, Object> withLot = body("Tabla La Cava", "Tablas");
        withLot.put("initialLot", Map.of("quantity", 0, "expiresOn", TODAY.toString()));

        as(Role.ADMIN, post(PRODUCTS), withLot)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("initialLot.quantity"))
                .andExpect(jsonPath("$.errors[0].message").value("La cantidad debe ser mayor que 0."));

        assertNothingCreated();
    }

    @Test
    @DisplayName("CA4 lote inicial sin cantidad: se crea el producto sin lote")
    void ca4InitialLotWithoutQuantity() throws Exception {
        Map<String, Object> withLot = body("Tabla vegetariana", "Tablas");
        withLot.put("initialLot", Map.of("expiresOn", TODAY.toString()));

        as(Role.ADMIN, post(PRODUCTS), withLot).andExpect(status().isCreated());

        assertThat(count("SELECT count(*) FROM product")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM stock_lot")).isZero();
    }

    // ---------- Existencia vigente ----------

    @Test
    @DisplayName("available suma solo lotes con vencimiento >= hoy (Clock fijo, zona Bogotá) y excluye vencidos")
    void availableExcludesExpiredLots() throws Exception {
        long id = createProduct(body("Postre de la casa", "Postres"));
        inventory.loadSampleLot(id, 2, TODAY.minusDays(1), null, NOW, adminId);
        inventory.loadSampleLot(id, 3, TODAY, null, NOW, adminId);
        inventory.loadSampleLot(id, 4, TODAY.plusDays(5), null, NOW, adminId);

        as(Role.WAITER, get(PRODUCTS), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].available").value(7));

        clock.fixAt(NOW.plusSeconds(86_400));
        as(Role.WAITER, get(PRODUCTS), null)
                .andExpect(jsonPath("$.content[0].available").value(4));
    }

    // ---------- Autorización ----------

    @ParameterizedTest
    @ValueSource(strings = { "CASHIER", "WAITER", "KITCHEN" })
    @DisplayName("POST, PUT y PATCH de productos dan 403 a cajero, mesero y preparación")
    void writesForbiddenForNonAdmin(Role role) throws Exception {
        long id = createProduct(body("Agua", "Bebidas"));

        as(role, post(PRODUCTS), body("Otro", "Bebidas")).andExpect(status().isForbidden());
        as(role, put(PRODUCTS + "/" + id), body("Agua grande", "Bebidas")).andExpect(status().isForbidden());
        as(role, patch(PRODUCTS + "/" + id + "/active"), Map.of("active", false)).andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM product")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM product", String.class)).isEqualTo("Agua");
    }

    @ParameterizedTest
    @ValueSource(strings = { "ADMIN", "CASHIER", "WAITER", "KITCHEN" })
    @DisplayName("GET /products y GET /categories están disponibles para los cuatro roles")
    void readsAllowedForAllRoles(Role role) throws Exception {
        createProduct(body("Agua", "Bebidas"));

        as(role, get(PRODUCTS), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Agua"));
        as(role, get("/api/v1/categories"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Bebidas"));
    }

    @Test
    @DisplayName("Sin token, GET /products da 401")
    void unauthenticatedGets401() throws Exception {
        mockMvc.perform(get(PRODUCTS)).andExpect(status().isUnauthorized());
    }

    // ---------- Listado: filtros, orden y paginación ----------

    @Test
    @DisplayName("GET /categories devuelve {id, name} ordenado por nombre")
    void categoriesSortedByName() throws Exception {
        createProduct(body("Copa de vino tinto", "Vinos"));
        createProduct(body("Agua", "Bebidas"));
        createProduct(body("Tabla La Cava", "Tablas"));

        as(Role.WAITER, get("/api/v1/categories"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(contains("Bebidas", "Tablas", "Vinos")))
                .andExpect(jsonPath("$[0].id").isNumber());
    }

    @Test
    @DisplayName("Listado: orden por nombre, filtro q (contiene, sin mayúsculas), categoryId y paginación")
    void listFiltersAndPagination() throws Exception {
        createProduct(body("Vino botella", "Vinos"));
        createProduct(body("Copa de vino tinto", "Vinos"));
        createProduct(body("Agua", "Bebidas"));
        createProduct(body("Vino caliente navideño", "Vinos"));
        createProduct(body("Limonada natural", "Bebidas"));

        as(Role.WAITER, get(PRODUCTS), null)
                .andExpect(jsonPath("$.content[*].name").value(contains("Agua", "Copa de vino tinto",
                        "Limonada natural", "Vino botella", "Vino caliente navideño")))
                .andExpect(jsonPath("$.totalElements").value(5));

        as(Role.WAITER, get(PRODUCTS).param("q", "VINO"), null)
                .andExpect(jsonPath("$.content[*].name").value(contains("Copa de vino tinto", "Vino botella",
                        "Vino caliente navideño")));

        long bebidas = jdbcTemplate.queryForObject("SELECT id FROM category WHERE name = 'Bebidas'", Long.class);
        as(Role.WAITER, get(PRODUCTS).param("categoryId", String.valueOf(bebidas)), null)
                .andExpect(jsonPath("$.content[*].name").value(contains("Agua", "Limonada natural")));

        as(Role.WAITER, get(PRODUCTS).param("page", "1").param("size", "2"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(contains("Limonada natural", "Vino botella")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        as(Role.WAITER, get(PRODUCTS).param("sort", "price,desc"), null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Listado: q con comodines de LIKE se busca literal")
    void listQueryEscapesWildcards() throws Exception {
        createProduct(body("Agua", "Bebidas"));

        as(Role.WAITER, get(PRODUCTS).param("q", "%"), null)
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Listado: ordenar por un campo no permitido da 400")
    void listRejectsUnknownSort() throws Exception {
        as(Role.WAITER, get(PRODUCTS).param("sort", "password"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    @Test
    @DisplayName("Listado: ordenar por una ruta anidada (category.name) da 400")
    void listRejectsNestedSort() throws Exception {
        as(Role.WAITER, get(PRODUCTS).param("sort", "category.name"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    // ---------- HU-03: disponibilidad y filtros ----------

    @Test
    @DisplayName("CA1 activo, en temporada y con existencia vigente es pedible, sin motivo de bloqueo")
    void hu03Ca1Orderable() throws Exception {
        long id = product("Tabla La Cava", null, true, 5, TODAY.plusDays(3));

        assertProduct(id, "ON_MENU", true, null);
    }

    @Test
    @DisplayName("CA1 producto retirado: orderable=false y blockReason \"Retirado de la carta\"")
    void hu03Ca1Inactive() throws Exception {
        long id = product("Agua", null, false, 5, TODAY.plusDays(3));

        assertProduct(id, "INACTIVE", false, "Retirado de la carta");
    }

    @Test
    @DisplayName("CA1 temporada vencida: orderable=false y blockReason \"Fuera de temporada\"")
    void hu03Ca1OutOfSeason() throws Exception {
        long id = product("Sangría de verano", TODAY.minusDays(10), true, 5, TODAY.plusDays(3));

        assertProduct(id, "OUT_OF_SEASON", false, "Fuera de temporada");
    }

    @Test
    @DisplayName("CA1 sin existencia vigente: orderable=false y blockReason \"Agotado\"")
    void hu03Ca1OutOfStock() throws Exception {
        long id = product("Tabla vegetariana", null, true, 0, null);

        assertProduct(id, "OUT_OF_STOCK", false, "Agotado");
    }

    @Test
    @DisplayName("CA1 un lote vencido no cuenta como existencia: el producto sale Agotado")
    void hu03Ca1ExpiredLotIsNotStock() throws Exception {
        long id = product("Postre de la casa", null, true, 5, TODAY.minusDays(1));

        assertProduct(id, "OUT_OF_STOCK", false, "Agotado");
    }

    @Test
    @DisplayName("CA1 un lote vigente con cantidad 0 no cuenta como existencia: Agotado y fuera de ON_MENU")
    void hu03Ca1ZeroQuantityLotIsNotStock() throws Exception {
        long id = product("Tabla de quesos", null, true, 5, TODAY.plusDays(9));
        jdbcTemplate.update("UPDATE stock_lot SET quantity_available = 0 WHERE product_id = ?", id);

        assertProduct(id, "OUT_OF_STOCK", false, "Agotado");
        as(Role.WAITER, get(PRODUCTS).param("state", "OUT_OF_STOCK"), null)
                .andExpect(jsonPath("$.content[*].name").value(hasItem("Tabla de quesos")));
        as(Role.WAITER, get(PRODUCTS).param("state", "ON_MENU"), null)
                .andExpect(jsonPath("$.content[*].name").value(not(hasItem("Tabla de quesos"))));
    }

    @Test
    @DisplayName("CA1 prioridad: inactivo y vencido gana \"Retirado de la carta\"; vencido y sin stock, \"Fuera de temporada\"")
    void hu03Ca1Priority() throws Exception {
        long inactiveAndExpired = product("Sangría de verano", TODAY.minusDays(1), false, 0, null);
        long expiredAndEmpty = product("Vino caliente navideño", TODAY.minusDays(1), true, 0, null);

        assertProduct(inactiveAndExpired, "INACTIVE", false, "Retirado de la carta");
        assertProduct(expiredAndEmpty, "OUT_OF_SEASON", false, "Fuera de temporada");
    }

    @Test
    @DisplayName("CA1 el ProductResponse trae state, orderable y blockReason también al retirar un producto")
    void hu03Ca1FieldsInPatch() throws Exception {
        long id = createProduct(body("Agua", "Bebidas"));

        as(Role.ADMIN, patch(PRODUCTS + "/" + id + "/active"), Map.of("active", false))
                .andExpect(jsonPath("$.state").value("INACTIVE"))
                .andExpect(jsonPath("$.orderable").value(false))
                .andExpect(jsonPath("$.blockReason").value("Retirado de la carta"));
    }

    @Test
    @DisplayName("CA2 filtro por estado: cada valor devuelve sus productos y su totalElements")
    void hu03Ca2StateFilters() throws Exception {
        seedAllStates();

        assertFilter("ON_MENU", "Copa de vino", "Vino caliente navideño");
        assertFilter("OUT_OF_STOCK", "Tabla vegetariana", "Vino de temporada agotado");
        assertFilter("SEASONAL", "Vino caliente navideño", "Vino de temporada agotado");
        assertFilter("OUT_OF_SEASON", "Sangría de verano");
        assertFilter("INACTIVE", "Agua");
    }

    @Test
    @DisplayName("CA2 el filtro de estado se combina con q y categoryId, y la paginación cuenta solo los filtrados")
    void hu03Ca2CombinedAndPaginated() throws Exception {
        seedAllStates();
        long vinos = jdbcTemplate.queryForObject("SELECT id FROM category WHERE name = 'Vinos'", Long.class);

        as(Role.WAITER, get(PRODUCTS).param("state", "ON_MENU").param("q", "navide"), null)
                .andExpect(jsonPath("$.content[*].name").value(contains("Vino caliente navideño")))
                .andExpect(jsonPath("$.totalElements").value(1));
        as(Role.WAITER, get(PRODUCTS).param("state", "ON_MENU").param("categoryId", String.valueOf(vinos)), null)
                .andExpect(jsonPath("$.content[*].name").value(contains("Copa de vino", "Vino caliente navideño")));
        as(Role.WAITER, get(PRODUCTS).param("state", "SEASONAL").param("q", "agotado"), null)
                .andExpect(jsonPath("$.content[*].name").value(contains("Vino de temporada agotado")));
        as(Role.WAITER, get(PRODUCTS).param("state", "OUT_OF_STOCK").param("size", "1").param("page", "1"), null)
                .andExpect(jsonPath("$.content[*].name").value(contains("Vino de temporada agotado")))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("CA2 un valor de state inválido da 400 con el error en el campo state")
    void hu03Ca2InvalidState() throws Exception {
        as(Role.WAITER, get(PRODUCTS).param("state", "AGOTADOS"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("state"));
    }

    @Test
    @DisplayName("CA3 con Clock fijo, una temporada que terminó ayer es Fuera de temporada; la que termina hoy sigue vigente")
    void hu03Ca3SeasonEndedYesterday() throws Exception {
        long ended = product("Sangría de verano", TODAY.minusDays(1), true, 5, TODAY.plusDays(30));
        long endsToday = product("Vino caliente navideño", TODAY, true, 5, TODAY.plusDays(30));

        assertProduct(ended, "OUT_OF_SEASON", false, "Fuera de temporada");
        assertProduct(endsToday, "ON_MENU", true, null);

        clock.fixAt(NOW.plusSeconds(86_400));
        assertProduct(endsToday, "OUT_OF_SEASON", false, "Fuera de temporada");
        as(Role.WAITER, get(PRODUCTS).param("state", "OUT_OF_SEASON"), null)
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ---------- Apoyo ----------

    /** Crea un producto (con temporada si seasonUntil != null) y, si qty > 0, un lote que vence en lotExpires. */
    private long product(String name, LocalDate seasonUntil, boolean active, int qty, LocalDate lotExpires)
            throws Exception {
        return product(name, "Vinos", seasonUntil, active, qty, lotExpires);
    }

    private long product(String name, String category, LocalDate seasonUntil, boolean active, int qty,
            LocalDate lotExpires) throws Exception {
        Map<String, Object> body = body(name, category);
        if (seasonUntil != null) {
            body.put("seasonName", "Temporada");
            body.put("seasonUntil", seasonUntil.toString());
        }
        long id = createProduct(body);
        if (qty > 0) {
            inventory.loadSampleLot(id, qty, lotExpires, null, NOW, adminId);
        }
        if (!active) {
            as(Role.ADMIN, patch(PRODUCTS + "/" + id + "/active"), Map.of("active", false)).andExpect(status().isOk());
        }
        return id;
    }

    private void assertProduct(long id, String state, boolean orderable, String blockReason) throws Exception {
        String content = as(Role.WAITER, get(PRODUCTS).param("size", "100"), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var node : json.readTree(content).get("content")) {
            if (node.get("id").asLong() == id) {
                assertThat(node.get("state").asString()).isEqualTo(state);
                assertThat(node.get("orderable").asBoolean()).isEqualTo(orderable);
                if (blockReason == null) {
                    assertThat(node.get("blockReason").isNull()).isTrue();
                } else {
                    assertThat(node.get("blockReason").asString()).isEqualTo(blockReason);
                }
                return;
            }
        }
        throw new AssertionError("El producto " + id + " no está en el listado");
    }

    private void assertFilter(String state, String... names) throws Exception {
        as(Role.WAITER, get(PRODUCTS).param("state", state), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(contains((Object[]) names)))
                .andExpect(jsonPath("$.totalElements").value(names.length));
    }

    /** Un producto en cada estado, más una temporada vigente sin existencia. */
    private void seedAllStates() throws Exception {
        product("Copa de vino", null, true, 5, TODAY.plusDays(9));                              // ON_MENU
        product("Vino caliente navideño", TODAY.plusDays(20), true, 5, TODAY.plusDays(9));      // ON_MENU y SEASONAL
        product("Tabla vegetariana", "Tablas", null, true, 0, null);                            // OUT_OF_STOCK
        product("Vino de temporada agotado", TODAY.plusDays(20), true, 5, TODAY.minusDays(1));  // OUT_OF_STOCK y SEASONAL
        product("Sangría de verano", TODAY.minusDays(1), true, 5, TODAY.plusDays(9));           // OUT_OF_SEASON
        product("Agua", "Bebidas", null, false, 5, TODAY.plusDays(9));                          // INACTIVE
    }

    private void assertNothingCreated() {
        assertThat(count("SELECT count(*) FROM product")).isZero();
        assertThat(count("SELECT count(*) FROM stock_lot")).isZero();
        assertThat(count("SELECT count(*) FROM stock_movement")).isZero();
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private static Map<String, Object> body(String name, String category) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("categoryName", category);
        body.put("price", 58000);
        body.put("description", "Quesos y charcutería");
        body.put("icon", "🧀");
        body.put("minStock", 4);
        return body;
    }

    private long createProduct(Map<String, Object> body) throws Exception {
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
