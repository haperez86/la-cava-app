package co.lacava.gestion.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/** Solo en pruebas: un {@code Clock} que cada prueba puede fijar (D16). */
@Configuration
@Profile("test")
class TestClockConfig {

    @Bean
    @Primary
    MutableTestClock testClock() {
        return new MutableTestClock();
    }
}
