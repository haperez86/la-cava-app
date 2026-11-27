package co.lacava.gestion.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del JWT (D6): secreto HS256 desde {@code JWT_SECRET} y duración de 10 horas.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration ttl) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET no está configurado o tiene menos de 32 caracteres. Revisa backend/.env.");
        }
        if (ttl == null) {
            ttl = Duration.ofHours(10);
        }
    }
}
