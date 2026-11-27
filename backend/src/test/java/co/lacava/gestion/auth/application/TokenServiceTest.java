package co.lacava.gestion.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import co.lacava.gestion.shared.security.JwtProperties;
import co.lacava.gestion.users.domain.AppUser;
import co.lacava.gestion.users.domain.Role;

class TokenServiceTest {

    private static final String SECRET = "unit-test-secret-for-la-cava-0123456789";
    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");

    private final SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final TokenService service = new TokenService(
            NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build(),
            new JwtProperties(SECRET, null), Clock.fixed(NOW, ZoneOffset.UTC));

    private AppUser user() {
        AppUser user = new AppUser("laura", "Laura Gómez", "hash", Role.WAITER, NOW);
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }

    @Test
    @DisplayName("CA1 el token lleva sub, rol y expira a las 10 h según el Clock")
    void issuesTokenWithClaimsAndTenHourExpiry() {
        IssuedToken issued = service.issue(user());

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(10)));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // El Clock de la prueba es fijo y pasado: se omite la validación de vigencia contra el reloj real.
        decoder.setJwtValidator(token -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());
        Jwt jwt = decoder
                .decode(issued.token());
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.<String>getClaim("iss")).isEqualTo("la-cava");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(10)));
        assertThat(jwt.<String>getClaim("role")).isEqualTo("WAITER");
        assertThat(jwt.<String>getClaim("username")).isEqualTo("laura");
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("corto", null)).isInstanceOf(IllegalStateException.class);
    }
}
