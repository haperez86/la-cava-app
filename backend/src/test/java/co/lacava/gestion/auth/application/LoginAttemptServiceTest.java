package co.lacava.gestion.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** P1: bloqueo tras 5 fallos seguidos durante 15 min. */
class LoginAttemptServiceTest {

    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-03-10T12:00:00Z"));
    private final LoginAttemptService service = new LoginAttemptService(clock, 5, LOCKOUT);

    private void fail(String username, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(username);
        }
    }

    @Test
    @DisplayName("5 fallos seguidos bloquean al usuario")
    void fiveFailuresLock() {
        fail("laura", 4);
        assertThat(service.isLocked("laura")).isFalse();

        service.recordFailure("laura");

        assertThat(service.isLocked("laura")).isTrue();
        assertThat(service.isLocked("  LAURA ")).as("usa el usuario normalizado").isTrue();
        assertThat(service.isLocked("jorge")).as("otros usuarios no se afectan").isFalse();
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
    @DisplayName("El bloqueo dura 15 min desde el 5.º fallo y luego el contador vuelve a 0")
    void lockExpiresAfterLockout() {
        fail("laura", 5);
        clock.advance(LOCKOUT.minusSeconds(1));
        assertThat(service.isLocked("laura")).isTrue();

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.isLocked("laura")).isFalse();

        fail("laura", 4);
        assertThat(service.isLocked("laura")).as("contador reiniciado tras el bloqueo").isFalse();
        service.recordFailure("laura");
        assertThat(service.isLocked("laura")).isTrue();
    }

    @Test
    @DisplayName("Los fallos durante el bloqueo no lo extienden")
    void failuresWhileLockedDoNotExtend() {
        fail("laura", 5);
        clock.advance(Duration.ofMinutes(10));
        fail("laura", 3);

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
    @DisplayName("Las entradas obsoletas se purgan para no crecer sin límite")
    void obsoleteEntriesArePurged() {
        for (int i = 0; i <= LoginAttemptService.PURGE_THRESHOLD; i++) {
            service.recordFailure("user" + i);
        }
        assertThat(service.trackedUsers()).isEqualTo(LoginAttemptService.PURGE_THRESHOLD + 1);

        clock.advance(LOCKOUT);
        service.recordFailure("otro");

        assertThat(service.trackedUsers()).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
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
