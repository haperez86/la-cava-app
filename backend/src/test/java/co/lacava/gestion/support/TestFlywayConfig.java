package co.lacava.gestion.support;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Solo en pruebas: deja el esquema de {@code la_cava_test} limpio y migrado al arrancar . */
@Configuration
@Profile("test")
class TestFlywayConfig {

    @Bean
    FlywayMigrationStrategy cleanMigrateStrategy() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
