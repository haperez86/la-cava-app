package co.lacava.gestion.shared.security;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Secreto con el que se firman los tokens de los QR de las mesas (D12, D35), desde {@code QR_SECRET}.
 * Como {@link JwtProperties}, la aplicación no arranca si falta o tiene menos de 32 bytes.
 */
@ConfigurationProperties(prefix = "app.qr")
public record QrProperties(String secret) {

    private static final int MIN_SECRET_BYTES = 32;

    public QrProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "QR_SECRET no está configurado o tiene menos de 32 caracteres. Revisa backend/.env.");
        }
    }

    /** No expone el secreto en logs ni en mensajes de error. */
    @Override
    public String toString() {
        return "QrProperties[secret=***]";
    }
}
