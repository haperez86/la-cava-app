package co.lacava.gestion.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import co.lacava.gestion.support.IntegrationTest;

/** LCV-HU-00: el contexto arranca con el perfil test y expone el Clock y la zona de negocio (D16). */
class TimeConfigIntegrationTest extends IntegrationTest {

    @Autowired
    Clock clock;

    @Autowired
    ZoneId businessZone;

    @Test
    void contextStartsWithInjectableClockAndBogotaZone() {
        assertThat(clock).isNotNull();
        assertThat(businessZone).isEqualTo(ZoneId.of("America/Bogota"));
    }
}
