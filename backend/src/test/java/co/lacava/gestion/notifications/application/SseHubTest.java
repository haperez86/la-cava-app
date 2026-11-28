package co.lacava.gestion.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseHubTest {

    @Test
    @DisplayName("Un emisor que falla se descarta y la entrada vacía del usuario se retira")
    void failedEmitterIsRemovedAndEmptyUserDropped() {
        SseHub hub = new SseHub();
        SseEmitter dead = hub.register(1L);
        hub.register(2L);
        dead.complete();

        hub.broadcast("heartbeat", "x");

        assertThat(hub.connections()).isEqualTo(1);
        assertThat(hub.trackedUsers()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un error inesperado de un emisor no corta el broadcast a los demás")
    void unexpectedErrorDoesNotStopBroadcast() {
        SseHub hub = new SseHub();
        SseEmitter bad = hub.register(1L);
        hub.register(2L);
        bad.completeWithError(new RuntimeException("boom"));

        hub.broadcast("heartbeat", "x");

        assertThat(hub.connections()).isEqualTo(1);
    }
}
