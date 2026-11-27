package co.lacava.gestion.tables.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import co.lacava.gestion.support.IntegrationTest;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.Role;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** LCV-HU-07: mesas y códigos QR (backend). */
class TableIntegrationTest extends IntegrationTest {

    private static final String TABLES = "/api/v1/tables";
    private static final String RESOLVE = TABLES + "/resolve";
    private static final String INVALID_CODE = "Código de mesa inválido";
    private static final Instant NOW = Instant.parse("2026-03-10T04:00:00Z");

    @Autowired
    private UserService users;

    private final ObjectMapper json = new ObjectMapper();
    private final Map<Role, String> tokens = new HashMap<>();

    @BeforeEach
    void createUsers() throws Exception {
        users.create("admin", "Administrador", "admin123", Role.ADMIN);
        users.create("camila", "Camila Torres", "camila123", Role.CASHIER);
        users.create("laura", "Laura Gómez", "laura123", Role.WAITER);
        users.create("jorge", "Jorge Ruiz", "jorge123", Role.KITCHEN);
        tokens.put(Role.ADMIN, tokenOf("admin"));
        tokens.put(Role.CASHIER, tokenOf("camila"));
        tokens.put(Role.WAITER, tokenOf("laura"));
        tokens.put(Role.KITCHEN, tokenOf("jorge"));
        clock.fixAt(NOW);
    }

    // ---------- CA1: crear y desactivar mesas, token HMAC y PIN ----------

    @Test
    @DisplayName("CA1 el administrador crea una mesa: 201 {id, number, active} y se guardan token HMAC y PIN de 4 dígitos")
    void ca1CreatesTable() throws Exception {
        as(Role.ADMIN, post(TABLES), Map.of("number", 5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.number").value(5))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.qrToken").doesNotExist())
                .andExpect(jsonPath("$.pin").doesNotExist());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT qr_token, pin, active, token_rotated_at, created_at FROM dining_table WHERE number = 5");
        assertThat((String) row.get("qr_token")).matches("[A-Za-z0-9_-]{43}");
        assertThat((String) row.get("pin")).matches("\\d{4}");
        assertThat(row.get("active")).isEqualTo(true);
        assertThat(row.get("token_rotated_at")).isNotNull();
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    @DisplayName("CA1 un número de mesa repetido da 409 y no crea otra mesa")
    void ca1DuplicateNumber() throws Exception {
        createTable(5);

        as(Role.ADMIN, post(TABLES), Map.of("number", 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una mesa con ese número."));

        assertThat(count("SELECT count(*) FROM dining_table")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "{\"number\":0}", "{\"number\":-3}", "{\"number\":2.5}", "{\"number\":null}" })
    @DisplayName("CA1 un número de mesa ausente, no entero o menor que 1 da 400 con errors[] en number")
    void ca1InvalidNumber(String body) throws Exception {
        mockMvc.perform(post(TABLES).header("Authorization", "Bearer " + tokens.get(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("number"));

        assertThat(count("SELECT count(*) FROM dining_table")).isZero();
    }

    @Test
    @DisplayName("CA1 el administrador desactiva y vuelve a activar una mesa")
    void ca1DeactivatesAndActivates() throws Exception {
        long id = createTable(3);

        as(Role.ADMIN, patch(TABLES + "/" + id + "/active"), Map.of("active", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.number").value(3))
                .andExpect(jsonPath("$.active").value(false));
        assertThat(jdbcTemplate.queryForObject("SELECT active FROM dining_table WHERE id = ?", Boolean.class, id))
                .isFalse();

        as(Role.ADMIN, patch(TABLES + "/" + id + "/active"), Map.of("active", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("CA1 activar/desactivar o regenerar una mesa que no existe da 404")
    void ca1UnknownTable() throws Exception {
        as(Role.ADMIN, patch(TABLES + "/999/active"), Map.of("active", false))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La mesa no existe."));
        as(Role.ADMIN, post(TABLES + "/999/rotate-token"), null)
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = { "CASHIER", "WAITER", "KITCHEN" })
    @DisplayName("CA1 solo el administrador crea, activa, regenera y ve la hoja de QR: 403 para los demás roles")
    void ca1WritesForbiddenForOtherRoles(Role role) throws Exception {
        long id = createTable(1);

        as(role, post(TABLES), Map.of("number", 2)).andExpect(status().isForbidden());
        as(role, patch(TABLES + "/" + id + "/active"), Map.of("active", false)).andExpect(status().isForbidden());
        as(role, post(TABLES + "/" + id + "/rotate-token"), null).andExpect(status().isForbidden());
        as(role, get(TABLES + "/qr-sheet"), null).andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM dining_table")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT active FROM dining_table WHERE id = ?", Boolean.class, id))
                .isTrue();
    }

    @Test
    @DisplayName("GET /tables lista por número sin token ni PIN para ADMIN, CASHIER y WAITER; KITCHEN recibe 403")
    void listWithoutSecrets() throws Exception {
        createTable(10);
        createTable(2);
        long inactive = createTable(7);
        as(Role.ADMIN, patch(TABLES + "/" + inactive + "/active"), Map.of("active", false));

        for (Role role : new Role[] { Role.ADMIN, Role.CASHIER, Role.WAITER }) {
            String body = as(role, get(TABLES), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].number", contains(2, 7, 10)))
                    .andExpect(jsonPath("$[*].active", contains(true, false, true)))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("qrToken", "pin", "code", "LACAVA");
            for (String secret : jdbcTemplate.queryForList("SELECT qr_token FROM dining_table", String.class)) {
                assertThat(body).doesNotContain(secret);
            }
        }
        as(Role.KITCHEN, get(TABLES), null).andExpect(status().isForbidden());
    }

    // ---------- CA2: hoja de QR imprimible ----------

    @Test
    @DisplayName("CA2 qr-sheet devuelve LACAVA:MESA:<n>:<token> y PIN de las mesas activas, ordenadas por número")
    void ca2QrSheet() throws Exception {
        createTable(9);
        createTable(1);
        long inactive = createTable(4);
        as(Role.ADMIN, patch(TABLES + "/" + inactive + "/active"), Map.of("active", false));

        as(Role.ADMIN, get(TABLES + "/qr-sheet"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].number", contains(1, 9)))
                .andExpect(jsonPath("$[0].code").value("LACAVA:MESA:1:" + tokenOfTable(1)))
                .andExpect(jsonPath("$[0].pin").value(pinOfTable(1)))
                .andExpect(jsonPath("$[1].code").value("LACAVA:MESA:9:" + tokenOfTable(9)))
                .andExpect(jsonPath("$[1].pin").value(pinOfTable(9)));
    }

    // ---------- CA3: regenerar invalida el QR anterior ----------

    @Test
    @DisplayName("CA3 tras rotate-token el código y el PIN viejos dan 404 \"Código de mesa inválido\" y los nuevos 200")
    void ca3RotateInvalidatesOldCode() throws Exception {
        long id = createTable(6);
        String oldCode = codeOf(6);
        String oldPin = pinOfTable(6);
        Instant rotatedBefore = rotatedAt(id);
        clock.fixAt(NOW.plusSeconds(3600));

        as(Role.ADMIN, post(TABLES + "/" + id + "/rotate-token"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.number").value(6))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.qrToken").doesNotExist())
                .andExpect(jsonPath("$.pin").doesNotExist());

        String newCode = codeOf(6);
        String newPin = pinOfTable(6);
        assertThat(newCode).isNotEqualTo(oldCode);
        assertThat(rotatedAt(id)).isAfter(rotatedBefore);

        as(Role.WAITER, post(RESOLVE), Map.of("code", oldCode))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(INVALID_CODE));
        if (!newPin.equals(oldPin)) {
            as(Role.WAITER, post(RESOLVE), Map.of("number", 6, "pin", oldPin))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value(INVALID_CODE));
        }
        as(Role.WAITER, post(RESOLVE), Map.of("code", newCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        as(Role.WAITER, post(RESOLVE), Map.of("number", 6, "pin", newPin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @DisplayName("CA3 tras rotate-token el PIN viejo deja de servir (PIN fijado antes de regenerar)")
    void ca3RotateInvalidatesOldPin() throws Exception {
        long id = createTable(8);
        // El PIN nuevo es aleatorio y puede coincidir con el anterior (1 en 10 000): se fija uno conocido antes.
        jdbcTemplate.update("UPDATE dining_table SET pin = '0000' WHERE id = ?", id);
        as(Role.ADMIN, post(TABLES + "/" + id + "/rotate-token"), null).andExpect(status().isOk());
        if ("0000".equals(pinOfTable(8))) {
            jdbcTemplate.update("UPDATE dining_table SET pin = '1111' WHERE id = ?", id);
        }

        as(Role.WAITER, post(RESOLVE), Map.of("number", 8, "pin", "0000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(INVALID_CODE));
    }

    // ---------- resolve y cuenta ----------

    @Test
    @DisplayName("resolve por código y por número + PIN devuelve la mesa con su cuenta vacía al mesero")
    void resolveByCodeAndPin() throws Exception {
        long id = createTable(4);

        as(Role.WAITER, post(RESOLVE), Map.of("code", codeOf(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.number").value(4))
                .andExpect(jsonPath("$.account.orders", hasSize(0)))
                .andExpect(jsonPath("$.account.total").value(0));
        as(Role.WAITER, post(RESOLVE), Map.of("number", 4, "pin", pinOfTable(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.account.total").value(0));
        as(Role.CASHIER, post(RESOLVE), Map.of("code", codeOf(4))).andExpect(status().isOk());
        as(Role.ADMIN, post(RESOLVE), Map.of("code", codeOf(4))).andExpect(status().isOk());
        as(Role.KITCHEN, post(RESOLVE), Map.of("code", codeOf(4))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("resolve: token de otra mesa, mesa inactiva, PIN errado, mesa inexistente o código mal formado dan el mismo 404")
    void resolveInvalidCasesShareMessage() throws Exception {
        createTable(1);
        long inactive = createTable(2);
        String tokenOfOne = tokenOfTable(1);
        String codeOfTwo = codeOf(2);
        as(Role.ADMIN, patch(TABLES + "/" + inactive + "/active"), Map.of("active", false));
        String wrongPin = pinOfTable(1).equals("9999") ? "0000" : "9999";

        Object[] bodies = {
                Map.of("code", "LACAVA:MESA:2:" + tokenOfOne),
                Map.of("code", codeOfTwo),
                Map.of("number", 2, "pin", pinOfTable(2)),
                Map.of("number", 1, "pin", wrongPin),
                Map.of("number", 77, "pin", "1234"),
                Map.of("code", "LACAVA:MESA:77:" + tokenOfOne),
                Map.of("code", "no es un QR de La Cava"),
                Map.of("code", "LACAVA:MESA:1:" + tokenOfOne + "x") };
        for (Object body : bodies) {
            as(Role.WAITER, post(RESOLVE), body)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value(INVALID_CODE));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "{\"number\":1}", "{\"pin\":\"1234\"}", "{\"code\":\"  \"}" })
    @DisplayName("resolve sin code ni number+pin da 400")
    void resolveWithoutDataIsBadRequest(String body) throws Exception {
        mockMvc.perform(post(RESOLVE).header("Authorization", "Bearer " + tokens.get(Role.WAITER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("code"));
    }

    @Test
    @DisplayName("GET /tables/{id}/account: ADMIN y CASHIER ven la cuenta vacía; WAITER y KITCHEN reciben 403; 404 si no existe")
    void accountAccess() throws Exception {
        long id = createTable(3);

        for (Role role : new Role[] { Role.ADMIN, Role.CASHIER }) {
            as(role, get(TABLES + "/" + id + "/account"), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.number").value(3))
                    .andExpect(jsonPath("$.account.orders", hasSize(0)))
                    .andExpect(jsonPath("$.account.total").value(0));
        }
        as(Role.WAITER, get(TABLES + "/" + id + "/account"), null).andExpect(status().isForbidden());
        as(Role.KITCHEN, get(TABLES + "/" + id + "/account"), null).andExpect(status().isForbidden());
        as(Role.ADMIN, get(TABLES + "/999/account"), null).andExpect(status().isNotFound());
    }

    // ---------- utilidades ----------

    private long createTable(int number) throws Exception {
        String body = as(Role.ADMIN, post(TABLES), Map.of("number", number))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("id").asLong();
    }

    private String tokenOfTable(int number) {
        return jdbcTemplate.queryForObject("SELECT qr_token FROM dining_table WHERE number = ?", String.class,
                number);
    }

    private String pinOfTable(int number) {
        return jdbcTemplate.queryForObject("SELECT pin FROM dining_table WHERE number = ?", String.class, number);
    }

    private String codeOf(int number) {
        return "LACAVA:MESA:" + number + ":" + tokenOfTable(number);
    }

    private Instant rotatedAt(long id) {
        return jdbcTemplate.queryForObject("SELECT token_rotated_at FROM dining_table WHERE id = ?",
                java.sql.Timestamp.class, id).toInstant();
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
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
