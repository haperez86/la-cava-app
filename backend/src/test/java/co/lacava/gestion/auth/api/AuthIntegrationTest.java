package co.lacava.gestion.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import co.lacava.gestion.support.IntegrationTest;
import co.lacava.gestion.users.application.UserService;
import co.lacava.gestion.users.domain.Role;
import tools.jackson.databind.ObjectMapper;

/** LCV-HU-01: inicio de sesión, /me y autorización por rol. */
class AuthIntegrationTest extends IntegrationTest {

    private static final String INVALID = "Usuario o contraseña incorrectos";

    @Autowired
    private UserService users;

    @Autowired
    private JwtEncoder encoder;

    private final ObjectMapper json = new ObjectMapper();

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))));
    }

    private String tokenOf(String username, String password) throws Exception {
        String body = login(username, password).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return json.readTree(body).get("token").asString();
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    @ParameterizedTest
    @CsvSource({ "admin,ADMIN", "camila,CASHIER", "laura,WAITER", "jorge,KITCHEN" })
    @DisplayName("CA1 login válido devuelve token, expiresAt y el rol para enrutar")
    void ca1LoginReturnsTokenAndRole(String username, Role role) throws Exception {
        users.create(username, "Nombre " + username, username + "123", role);

        login(username, username + "123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.user.fullName").value("Nombre " + username))
                .andExpect(jsonPath("$.user.role").value(role.name()));
    }

    @Test
    @DisplayName("CA1 /me con token devuelve el usuario y rol de la sesión")
    void ca1MeWithToken() throws Exception {
        users.create("laura", "Laura Gómez", "laura123", Role.WAITER);

        mockMvc.perform(withToken(get("/api/v1/auth/me"), tokenOf("laura", "laura123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Laura Gómez"))
                .andExpect(jsonPath("$.role").value("WAITER"));
    }

    @Test
    @DisplayName("CA1 el usuario no distingue mayúsculas ni espacios")
    void ca1UsernameCaseInsensitive() throws Exception {
        users.create("laura", "Laura Gómez", "laura123", Role.WAITER);

        login("  LAURA ", "laura123").andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("WAITER"));
    }

    @Test
    @DisplayName("CA2 contraseña errada → 401 con el mensaje único")
    void ca2WrongPassword() throws Exception {
        users.create("laura", "Laura Gómez", "laura123", Role.WAITER);

        login("laura", "otra-clave").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value(INVALID));
    }

    @Test
    @DisplayName("CA2 usuario desconocido → 401 con el mismo mensaje")
    void ca2UnknownUser() throws Exception {
        login("nadie", "laura123").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value(INVALID));
    }

    @Test
    @DisplayName("CA2 usuario inactivo → 401 con el mismo mensaje")
    void ca2InactiveUser() throws Exception {
        users.create("laura", "Laura Gómez", "laura123", Role.WAITER);
        jdbcTemplate.update("UPDATE app_user SET active = false WHERE username = 'laura'");

        login("laura", "laura123").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value(INVALID));
    }

    @Test
    @DisplayName("P1 tras 5 claves erradas, la correcta también responde 401 con el mismo mensaje")
    void p1LockedAfterFiveFailures() throws Exception {
        users.create("laura", "Laura Gómez", "laura123", Role.WAITER);
        for (int i = 0; i < 5; i++) {
            login("laura", "otra-clave").andExpect(status().isUnauthorized());
        }

        login("LAURA", "laura123").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value(INVALID));
    }

    @Test
    @DisplayName("P1 el bloqueo de un usuario no afecta a otro; 4 fallos y un éxito reinician el contador")
    void p1LockIsPerUser() throws Exception {
        users.create("laura", "Laura Gómez", "laura123", Role.WAITER);
        users.create("jorge", "Jorge Ruiz", "jorge123", Role.KITCHEN);
        for (int i = 0; i < 5; i++) {
            login("laura", "otra-clave").andExpect(status().isUnauthorized());
        }
        for (int i = 0; i < 4; i++) {
            login("jorge", "otra-clave").andExpect(status().isUnauthorized());
        }

        login("jorge", "jorge123").andExpect(status().isOk());
        login("jorge", "otra-clave").andExpect(status().isUnauthorized());
        login("jorge", "jorge123").andExpect(status().isOk());
        login("laura", "laura123").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P1 un usuario inexistente también se bloquea con el mismo mensaje")
    void p1UnknownUserAlsoCounts() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("nadie", "x").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value(INVALID));
        }
        login("nadie", "x").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value(INVALID));
    }

    @Test
    @DisplayName("CA2 el token de un usuario desactivado deja de servir")
    void ca2TokenOfDeactivatedUserRejected() throws Exception {
        users.create("laura", "Laura Gómez", "laura123", Role.WAITER);
        String token = tokenOf("laura", "laura123");
        jdbcTemplate.update("UPDATE app_user SET active = false WHERE username = 'laura'");

        mockMvc.perform(withToken(get("/api/v1/auth/me"), token)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un token con el mismo secreto pero otro emisor → 401")
    void tokenWithForeignIssuerRejected() throws Exception {
        users.create("laura", "Laura Gómez", "laura123", Role.WAITER);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE username = 'laura'", Long.class);
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("otro-emisor").subject(String.valueOf(id))
                .issuedAt(now).expiresAt(now.plusSeconds(600)).claim("username", "laura").claim("role", "WAITER")
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mockMvc.perform(withToken(get("/api/v1/auth/me"), token)).andExpect(status().isUnauthorized());
        mockMvc.perform(withToken(get("/api/v1/auth/me"), tokenOf("laura", "laura123"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Login con campos vacíos → 400")
    void emptyFieldsAre400() throws Exception {
        login("", "").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("/me sin token → 401")
    void meWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CA4 endpoint solo-ADMIN: mesero y preparación → 403, admin → 200")
    void ca4AdminOnly() throws Exception {
        users.create("admin", "Admin", "admin123", Role.ADMIN);
        users.create("laura", "Laura", "laura123", Role.WAITER);
        users.create("jorge", "Jorge", "jorge123", Role.KITCHEN);

        for (String u : new String[] { "laura", "jorge" }) {
            mockMvc.perform(withToken(get("/api/v1/test/roles/admin-only"), tokenOf(u, u + "123")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
        mockMvc.perform(withToken(get("/api/v1/test/roles/admin-only"), tokenOf("admin", "admin123")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CA4 endpoint solo-CASHIER: mesero y preparación → 403, cajero → 200")
    void ca4CashierOnly() throws Exception {
        users.create("camila", "Camila", "camila123", Role.CASHIER);
        users.create("laura", "Laura", "laura123", Role.WAITER);
        users.create("jorge", "Jorge", "jorge123", Role.KITCHEN);

        for (String u : new String[] { "laura", "jorge" }) {
            mockMvc.perform(withToken(get("/api/v1/test/roles/cashier-only"), tokenOf(u, u + "123")))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(withToken(get("/api/v1/test/roles/cashier-only"), tokenOf("camila", "camila123")))
                .andExpect(status().isOk());
    }
}
