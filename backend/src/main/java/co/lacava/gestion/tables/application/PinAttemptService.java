package co.lacava.gestion.tables.application;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import co.lacava.gestion.shared.security.AttemptLimiter;

/**
 * Limita los intentos de abrir una mesa con número y PIN (D36, mismo patrón que D27): tras
 * {@code maxFailedAttempts} fallos seguidos por usuario, bloqueo de {@code lockout} en memoria (D9). El código del
 * QR no pasa por aquí. El aviso de bloqueo lleva usuario y número de mesa, nunca el PIN.
 */
@Service
public class PinAttemptService {

    private static final Logger log = LoggerFactory.getLogger(PinAttemptService.class);

    private final AttemptLimiter limiter;

    public PinAttemptService(Clock clock,
            @Value("${app.tables.pin.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.tables.pin.lockout:PT15M}") Duration lockout) {
        this.limiter = new AttemptLimiter("PIN de mesa", clock, maxFailedAttempts, lockout);
    }

    /** Reserva un intento del usuario de forma atómica, antes de comparar el PIN. */
    public AttemptLimiter.Permit tryAcquire(long userId) {
        return limiter.tryAcquire(key(userId));
    }

    /** El intento reservado falló; si inicia el bloqueo, se registra un aviso con usuario y mesa (sin PIN). */
    public void recordFailure(AttemptLimiter.Permit permit, long userId, String username, int tableNumber) {
        if (permit.allowed() && permit.locksOnFailure()) {
            log.warn("Apertura de mesa por PIN bloqueada durante {} min para el usuario '{}' (id {}) tras {} intentos "
                    + "fallidos; último intento: mesa {}.",
                    limiter.lockout().toMinutes(), username, userId, limiter.maxFailedAttempts(), tableNumber);
        }
    }

    /** Un PIN correcto reinicia el contador del usuario. */
    public void recordSuccess(long userId) {
        limiter.recordSuccess(key(userId));
    }

    /** {@code true} si el usuario está bloqueado ahora. */
    public boolean isLocked(long userId) {
        return limiter.isLocked(key(userId));
    }

    /** Solo para pruebas: olvida todos los intentos. */
    public void clear() {
        limiter.clear();
    }

    private static String key(long userId) {
        return Long.toString(userId);
    }
}
