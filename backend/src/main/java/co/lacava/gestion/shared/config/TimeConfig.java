package co.lacava.gestion.shared.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj inyectable (UTC) para poder fijar el tiempo en pruebas, y zona del "día de negocio".
 */
@Configuration
class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ZoneId businessZone(@Value("${app.business-zone:America/Bogota}") String zone) {
        return ZoneId.of(zone);
    }
}
