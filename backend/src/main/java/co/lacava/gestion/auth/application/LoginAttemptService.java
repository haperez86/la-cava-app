package co.lacava.gestion.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import co.lacava.gestion.users.application.UserService;

/**
 * Limita los intentos de inicio de sesión (D27): como máximo {@code maxFailedAttempts} intentos seguidos sin éxito
 * por usuario; al agotarlos, lo bloquea durante {@code lockout}. Vive en memoria de una sola instancia (D9) y toma
 * el tiempo del {@link Clock} inyectado (D16). Cuenta igual usuarios existentes e inexistentes.
 *
 * <p>El intento se reserva de forma atómica ({@link #tryAcquire}) <em>antes</em> de comparar la contraseña, para que
 * una ráfaga concurrente no obtenga más intentos que el máximo. Un éxito ({@link #recordSuccess}) reinicia el
 * contador.
 *
 * <p>Memoria acotada: las entradas obsoletas se barren como máximo una vez por {@link #SWEEP_INTERVAL}, y por encima
 * de {@link #MAX_ENTRIES} se descartan las más antiguas que no estén bloqueadas (las bloqueadas se conservan).
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /** Tope de usuarios seguidos a la vez (salvo bloqueados, que nunca se descartan). */
    static final int MAX_ENTRIES = 10_000;
    /** Al superar el tope se descarta hasta este tamaño, para no ordenar el mapa en cada petición. */
    static final int EVICT_TO = MAX_ENTRIES * 9 / 10;
    /** Frecuencia máxima del barrido de entradas obsoletas. */
    static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);

    private final ConcurrentMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastSweep = new AtomicReference<>(Instant.MIN);
    private final ReentrantLock evictionLock = new ReentrantLock();
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

    /**
     * Reserva un intento de forma atómica. Si el usuario está bloqueado devuelve un permiso denegado sin cambiar
     * nada (los intentos durante el bloqueo no lo extienden). Si no, cuenta el intento y, si es el último permitido,
     * deja fijado el bloqueo desde ahora: un éxito posterior ({@link #recordSuccess}) lo levanta.
     */
    public Permit tryAcquire(String username) {
        Instant now = clock.instant();
        String key = key(username);
        boolean[] allowed = new boolean[1];
        Attempts updated = attempts.compute(key, (k, a) -> {
            Attempts current = (a == null || a.isObsolete(now, lockout)) ? null : a;
            if (current != null && current.isLockedAt(now)) {
                return current;
            }
            allowed[0] = true;
            int count = current == null ? 1 : current.count() + 1;
            Instant lockedUntil = count >= maxFailedAttempts ? now.plus(lockout) : null;
            return new Attempts(count, now, lockedUntil);
        });
        housekeeping(now);
        boolean last = allowed[0] && updated.lockedUntil() != null;
        return new Permit(key, allowed[0], last);
    }

    /**
     * El intento reservado falló. El contador ya se incrementó en {@link #tryAcquire}; aquí solo se deja rastro
     * cuando ese fallo inicia el bloqueo.
     */
    public void recordFailure(Permit permit) {
        if (permit.allowed() && permit.locksOnFailure()) {
            log.warn("Inicio de sesión bloqueado durante {} min para el usuario '{}' tras {} intentos fallidos.",
                    lockout.toMinutes(), permit.username(), maxFailedAttempts);
        }
    }

    /** Un inicio de sesión exitoso reinicia el contador. */
    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    /** {@code true} si el usuario está bloqueado ahora. No modifica el contador. */
    public boolean isLocked(String username) {
        Attempts current = attempts.get(key(username));
        return current != null && current.isLockedAt(clock.instant());
    }

    /** Solo para pruebas: olvida todos los intentos. */
    public void clear() {
        attempts.clear();
        lastSweep.set(Instant.MIN);
    }

    int trackedUsers() {
        return attempts.size();
    }

    private void housekeeping(Instant now) {
        Instant previous = lastSweep.get();
        if (!now.isBefore(previous.plus(SWEEP_INTERVAL)) && lastSweep.compareAndSet(previous, now)) {
            attempts.entrySet().removeIf(e -> e.getValue().isObsolete(now, lockout));
        }
        if (attempts.size() > MAX_ENTRIES && evictionLock.tryLock()) {
            try {
                evictOldestUnlocked(now);
            } finally {
                evictionLock.unlock();
            }
        }
    }

    /** Descarta las entradas no bloqueadas más antiguas hasta {@link #EVICT_TO}. Las bloqueadas se conservan. */
    private void evictOldestUnlocked(Instant now) {
        int excess = attempts.size() - EVICT_TO;
        if (excess <= 0) {
            return;
        }
        List<Map.Entry<String, Attempts>> candidates = attempts.entrySet().stream()
                .filter(e -> !e.getValue().isLockedAt(now))
                .map(e -> Map.entry(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(e -> e.getValue().lastAttempt()))
                .limit(excess)
                .toList();
        // remove(key, value): si la entrada cambió mientras tanto, se conserva.
        candidates.forEach(e -> attempts.remove(e.getKey(), e.getValue()));
        log.warn("Se descartaron {} registros de intentos de inicio de sesión por superar el tope de {}.",
                candidates.size(), MAX_ENTRIES);
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

    /**
     * @param count       intentos seguidos sin éxito (incluye los que están en curso)
     * @param lastAttempt instante del último intento contado
     * @param lockedUntil fin del bloqueo, o {@code null} si no está bloqueado
     */
    private record Attempts(int count, Instant lastAttempt, Instant lockedUntil) {

        boolean isLockedAt(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        /** Bloqueo vencido, o sin bloqueo y el último intento es más viejo que la ventana: el contador vuelve a 0. */
        boolean isObsolete(Instant now, Duration window) {
            if (lockedUntil != null) {
                return !now.isBefore(lockedUntil);
            }
            return !now.isBefore(lastAttempt.plus(window));
        }
    }
}
