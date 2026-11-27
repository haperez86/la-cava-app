package co.lacava.gestion.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import co.lacava.gestion.users.application.UserService;

/**
 * Limita los intentos de inicio de sesión (P1): tras {@code maxFailedAttempts} fallos seguidos para el mismo
 * usuario, lo bloquea durante {@code lockout}. Vive en memoria de una sola instancia (D9) y toma el tiempo del
 * {@link Clock} inyectado (D16). Cuenta igual usuarios existentes e inexistentes.
 */
@Service
public class LoginAttemptService {

    /** Por encima de este tamaño se purgan las entradas obsoletas al registrar un fallo. */
    static final int PURGE_THRESHOLD = 1_000;

    private final ConcurrentMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxFailedAttempts;
    private final Duration lockout;

    public LoginAttemptService(Clock clock,
            @Value("${app.auth.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.auth.lockout:PT15M}") Duration lockout) {
        this.clock = clock;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockout = lockout;
    }

    /** {@code true} si el usuario está bloqueado ahora. Un bloqueo vencido deja el contador en 0. */
    public boolean isLocked(String username) {
        Instant now = clock.instant();
        Attempts current = attempts.computeIfPresent(key(username), (k, a) -> a.isObsolete(now, lockout) ? null : a);
        return current != null && current.isLockedAt(now);
    }

    /** Registra un fallo; al llegar al máximo, bloquea durante {@code lockout} desde este fallo. */
    public void recordFailure(String username) {
        Instant now = clock.instant();
        attempts.compute(key(username), (k, a) -> {
            if (a != null && a.isLockedAt(now)) {
                return a;
            }
            int failures = (a == null || a.isObsolete(now, lockout)) ? 1 : a.failures() + 1;
            Instant lockedUntil = failures >= maxFailedAttempts ? now.plus(lockout) : null;
            return new Attempts(failures, now, lockedUntil);
        });
        if (attempts.size() > PURGE_THRESHOLD) {
            attempts.entrySet().removeIf(e -> e.getValue().isObsolete(now, lockout));
        }
    }

    /** Un inicio de sesión exitoso reinicia el contador. */
    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    /** Solo para pruebas: olvida todos los intentos. */
    public void clear() {
        attempts.clear();
    }

    int trackedUsers() {
        return attempts.size();
    }

    private static String key(String username) {
        return UserService.normalizeUsername(username);
    }

    /**
     * @param lockedUntil fin del bloqueo, o {@code null} si no está bloqueado
     */
    private record Attempts(int failures, Instant lastFailure, Instant lockedUntil) {

        boolean isLockedAt(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        /** Bloqueo vencido, o sin bloqueo y el último fallo es más viejo que la ventana: el contador vuelve a 0. */
        boolean isObsolete(Instant now, Duration window) {
            if (lockedUntil != null) {
                return !now.isBefore(lockedUntil);
            }
            return !now.isBefore(lastFailure.plus(window));
        }
    }
}
