package co.lacava.gestion.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Reloj de las pruebas de integración: sigue al reloj del sistema hasta que una prueba lo fija con {@link #fixAt}.
 * {@link IntegrationTest} lo devuelve al reloj del sistema antes de cada prueba.
 */
public final class MutableTestClock extends Clock {

    private volatile Instant fixed;

    public void fixAt(Instant instant) {
        this.fixed = instant;
    }

    public void useSystemClock() {
        this.fixed = null;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant(), zone);
    }

    @Override
    public Instant instant() {
        Instant current = fixed;
        return current != null ? current : Instant.now();
    }
}
