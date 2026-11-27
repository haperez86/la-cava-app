package co.lacava.gestion.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** D27: como máximo 5 intentos seguidos sin éxito; luego, bloqueo de 15 min. */
@ExtendWith(OutputCaptureExtension.class)
class LoginAttemptServiceTest {

    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-03-10T12:00:00Z"));
    private final LoginAttemptService service = new LoginAttemptService(clock, 5, LOCKOUT);

    private void fail(String username, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(service.tryAcquire(username));
        }
    }

    @Test
    @DisplayName("5 fallos seguidos bloquean al usuario")
    void fiveFailuresLock() {
        fail("laura", 4);
        assertThat(service.isLocked("laura")).isFalse();

        fail("laura", 1);

        assertThat(service.isLocked("laura")).isTrue();
        assertThat(service.isLocked("  LAURA ")).as("usa el usuario normalizado").isTrue();
        assertThat(service.isLocked("jorge")).as("otros usuarios no se afectan").isFalse();
        assertThat(service.tryAcquire("laura").allowed()).as("el 6.º intento se rechaza").isFalse();
    }

    @Test
    @DisplayName("4 fallos y un éxito reinician el contador")
    void successResetsCounter() {
        fail("laura", 4);
        service.recordSuccess("laura");

        fail("laura", 4);

        assertThat(service.isLocked("laura")).isFalse();
    }

    @Test
    @DisplayName("El 5.º intento se permite y, si acierta, reinicia el contador")
    void fifthAttemptCanSucceed() {
        fail("laura", 4);

        LoginAttemptService.Permit fifth = service.tryAcquire("laura");
        assertThat(fifth.allowed()).isTrue();
        assertThat(fifth.locksOnFailure()).isTrue();
        service.recordSuccess("laura");

        assertThat(service.isLocked("laura")).isFalse();
        assertThat(service.tryAcquire("laura").locksOnFailure()).isFalse();
    }

    @Test
    @DisplayName("El bloqueo dura 15 min desde el 5.º fallo y luego el contador vuelve a 0")
    void lockExpiresAfterLockout() {
        fail("laura", 5);
        clock.advance(LOCKOUT.minusSeconds(1));
        assertThat(service.isLocked("laura")).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.isLocked("laura")).isFalse();

        fail("laura", 4);
        assertThat(service.isLocked("laura")).as("contador reiniciado tras el bloqueo").isFalse();
        fail("laura", 1);
        assertThat(service.isLocked("laura")).isTrue();
    }

    @Test
    @DisplayName("Los intentos durante el bloqueo se rechazan y no lo extienden")
    void attemptsWhileLockedDoNotExtend() {
        fail("laura", 5);
        clock.advance(Duration.ofMinutes(10));
        for (int i = 0; i < 3; i++) {
            assertThat(service.tryAcquire("laura").allowed()).isFalse();
        }

        clock.advance(Duration.ofMinutes(5));

        assertThat(service.isLocked("laura")).isFalse();
    }

    @Test
    @DisplayName("Un usuario inexistente también se cuenta y se bloquea")
    void unknownUserCounts() {
        fail("nadie", 5);

        assertThat(service.isLocked("nadie")).isTrue();
    }

    @Test
    @DisplayName("Una ráfaga concurrente de 20 intentos solo obtiene 5")
    void concurrentBurstGetsAtMostMaxAttempts() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.tryAcquire("laura").allowed();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(5);
            assertThat(service.isLocked("laura")).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Las entradas obsoletas se barren como máximo una vez por minuto")
    void obsoleteEntriesAreSweptAtMostOncePerMinute() {
        Instant t0 = clock.instant();
        service.tryAcquire("a");
        clock.set(t0.plusSeconds(50));
        service.tryAcquire("b");

        clock.set(t0.plus(LOCKOUT).plusSeconds(10));
        service.tryAcquire("c");
        assertThat(service.trackedUsers()).as("'a' obsoleta y barrida").isEqualTo(2);

        clock.advance(Duration.ofSeconds(45));
        service.tryAcquire("d");
        assertThat(service.trackedUsers()).as("'b' ya es obsoleta, pero no ha pasado un minuto").isEqualTo(3);

        clock.advance(Duration.ofSeconds(15));
        service.tryAcquire("e");
        assertThat(service.trackedUsers()).as("barrido al cumplirse el minuto").isEqualTo(3);
    }

    @Test
    @DisplayName("Por encima del tope se descartan las entradas más antiguas, pero nunca las bloqueadas")
    void hardCapEvictsOldestUnlockedEntries() {
        fail("victima", 5);
        clock.advance(Duration.ofSeconds(1));
        fail("antigua", 4);

        for (int i = 0; i < LoginAttemptService.MAX_ENTRIES; i++) {
            clock.advance(Duration.ofMillis(1));
            service.tryAcquire("user" + i);
        }

        assertThat(service.trackedUsers()).isLessThanOrEqualTo(LoginAttemptService.MAX_ENTRIES);
        assertThat(service.isLocked("victima")).as("la entrada bloqueada se conserva").isTrue();
        assertThat(service.tryAcquire("antigua").locksOnFailure())
                .as("la entrada no bloqueada más antigua se descartó: su contador volvió a 0")
                .isFalse();
        assertThat(service.trackedUsers()).isLessThanOrEqualTo(LoginAttemptService.MAX_ENTRIES);
    }

    @Test
    @DisplayName("Al empezar un bloqueo se registra un aviso con el usuario")
    void logsWarningWhenLockStarts(CapturedOutput output) {
        fail("  Laura ", 4);
        assertThat(output).doesNotContain("Inicio de sesión bloqueado");

        fail("  Laura ", 1);

        assertThat(output).contains("WARN").contains("Inicio de sesión bloqueado").contains("'laura'");
    }

    @Test
    @DisplayName("Si el 5.º intento acierta no se registra bloqueo")
    void noWarningWhenFifthAttemptSucceeds(CapturedOutput output) {
        fail("laura", 4);
        service.tryAcquire("laura");
        service.recordSuccess("laura");

        assertThat(output).doesNotContain("Inicio de sesión bloqueado");
    }

    private static final class MutableClock extends Clock {

        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
