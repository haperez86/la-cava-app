package co.lacava.gestion.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** LCV-HU-07: el arranque exige un {@code QR_SECRET} de al menos 32 bytes (como {@code JWT_SECRET}). */
class QrPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(QrConfig.class);

    @Test
    @DisplayName("El arranque falla si QR_SECRET tiene menos de 32 bytes")
    void startupFailsWithShortSecret() {
        runner.withPropertyValues("app.qr.secret=corto-de-31-caracteres-01234567").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("QR_SECRET");
        });
    }

    @Test
    @DisplayName("El arranque falla si QR_SECRET no está configurado")
    void startupFailsWithoutSecret() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Con un QR_SECRET de 32 bytes arranca y no expone el secreto en toString")
    void startsWithValidSecret() {
        String secret = "secreto-de-32-caracteres-0123456";
        runner.withPropertyValues("app.qr.secret=" + secret).run(context -> {
            assertThat(context).hasNotFailed();
            QrProperties properties = context.getBean(QrProperties.class);
            assertThat(properties.secret()).isEqualTo(secret);
            assertThat(properties.toString()).doesNotContain(secret);
        });
        assertThatThrownBy(() -> new QrProperties(null)).isInstanceOf(IllegalStateException.class);
    }

    @Configuration
    @EnableConfigurationProperties(QrProperties.class)
    static class QrConfig {
    }
}
