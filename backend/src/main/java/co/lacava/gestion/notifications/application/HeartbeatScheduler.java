package co.lacava.gestion.notifications.application;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** {@code heartbeat} cada 25 s (configurable en pruebas) para que proxies y navegadores no cierren el stream. */
@Component
class HeartbeatScheduler {

    private final SseHub hub;
    private final Clock clock;

    HeartbeatScheduler(SseHub hub, Clock clock) {
        this.hub = hub;
        this.clock = clock;
    }

    @Scheduled(fixedRateString = "${app.sse.heartbeat-ms:25000}")
    void beat() {
        hub.broadcast("heartbeat", clock.instant().toString());
    }
}
