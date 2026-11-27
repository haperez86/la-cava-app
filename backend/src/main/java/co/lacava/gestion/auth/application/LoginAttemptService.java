package co.lacava.gestion.auth.application;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import co.lacava.gestion.shared.security.AttemptLimiter;
import co.lacava.gestion.users.application.UserService;

/**
 * Limita los intentos de inicio de sesión (D27): como máximo {@code maxFailedAttempts} intentos seguidos sin éxito
 * por usuario; al agotarlos, lo bloquea durante {@code lockout}. Vive en memoria de una sola instancia (D9) y toma
 * el tiempo del {@link Clock} inyectado (D16). Cuenta igual usuarios existentes e inexistentes.
 *
 * <p>El intento se reserva de forma atómica ({@link #tryAcquire}) <em>antes</em> de comparar la contraseña, para que
 * una ráfaga concurrente no obtenga más intentos que el máximo. Un éxito ({@link #recordSuccess}) reinicia el
 * contador. El conteo, el bloqueo y la memoria acotada los lleva {@link AttemptLimiter}.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /** Tope de usuarios seguidos a la vez (salvo bloqueados, que nunca se descartan). */
    static final int MAX_ENTRIES = AttemptLimiter.MAX_ENTRIES;

    private final AttemptLimiter limiter;

    public LoginAttemptService(Clock clock,
            @Value("${app.auth.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.auth.lockout:PT15M}") Duration lockout) {
        this.limiter = new AttemptLimiter("inicio de sesión", clock, maxFailedAttempts, lockout);
    }

    /**
     * Reserva un intento de forma atómica. Si el usuario está bloqueado devuelve un permiso denegado sin cambiar
     * nada (los intentos durante el bloqueo no lo extienden). Si no, cuenta el intento y, si es el último permitido,
     * deja fijado el bloqueo desde ahora: un éxito posterior ({@link #recordSuccess}) lo levanta.
     */
    public Permit tryAcquire(String username) {
        AttemptLimiter.Permit permit = limiter.tryAcquire(key(username));
        return new Permit(permit.key(), permit.allowed(), permit.locksOnFailure());
    }

    /**
     * El intento reservado falló. El contador ya se incrementó en {@link #tryAcquire}; aquí solo se deja rastro
     * cuando ese fallo inicia el bloqueo.
     */
    public void recordFailure(Permit permit) {
        if (permit.allowed() && permit.locksOnFailure()) {
            log.warn("Inicio de sesión bloqueado durante {} min para el usuario '{}' tras {} intentos fallidos.",
                    limiter.lockout().toMinutes(), permit.username(), limiter.maxFailedAttempts());
        }
    }

    /** Un inicio de sesión exitoso reinicia el contador. */
    public void recordSuccess(String username) {
        limiter.recordSuccess(key(username));
    }

    /** {@code true} si el usuario está bloqueado ahora. No modifica el contador. */
    public boolean isLocked(String username) {
        return limiter.isLocked(key(username));
    }

    /** Solo para pruebas: olvida todos los intentos. */
    public void clear() {
        limiter.clear();
    }

    int trackedUsers() {
        return limiter.trackedKeys();
    }

    private static String key(String username) {
        return UserService.normalizeUsername(username);
    }

    /**
     * Resultado de {@link #tryAcquire}.
     *
     * @param username       usuario normalizado
     * @param allowed        {@code false} si el usuario está bloqueado y el intento no cuenta
     * @param locksOnFailure {@code true} si es el último intento permitido: si falla, queda bloqueado
     */
    public record Permit(String username, boolean allowed, boolean locksOnFailure) {
    }
}
