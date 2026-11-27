package co.lacava.gestion.shared.security;

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

/**
 * Limitador genérico de intentos seguidos sin éxito por clave (D27, D36): como máximo {@code maxFailedAttempts}
 * intentos; al agotarlos, la clave queda bloqueada durante {@code lockout}. Vive en memoria de una sola instancia (D9)
 * y toma el tiempo del {@link Clock} inyectado (D16).
 *
 * <p>El intento se reserva de forma atómica ({@link #tryAcquire}) <em>antes</em> de comparar el secreto, para que una
 * ráfaga concurrente no obtenga más intentos que el máximo. Un éxito ({@link #recordSuccess}) reinicia el contador.
 * Los intentos durante el bloqueo se rechazan y no lo extienden.
 *
 * <p>Memoria acotada: las entradas obsoletas se barren como máximo una vez por {@link #SWEEP_INTERVAL}, y por encima
 * de {@link #MAX_ENTRIES} se descartan las más antiguas que no estén bloqueadas (las bloqueadas se conservan).
 *
 * <p>No es un bean: cada servicio crea el suyo con su nombre y sus límites, y registra su propio aviso de bloqueo.
 */
public final class AttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(AttemptLimiter.class);

    /** Tope de claves seguidas a la vez (salvo bloqueadas, que nunca se descartan). */
    public static final int MAX_ENTRIES = 10_000;
    /** Al superar el tope se descarta hasta este tamaño, para no ordenar el mapa en cada petición. */
    public static final int EVICT_TO = MAX_ENTRIES * 9 / 10;
    /** Frecuencia máxima del barrido de entradas obsoletas. */
    public static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);

    private final ConcurrentMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastSweep = new AtomicReference<>(Instant.MIN);
    private final ReentrantLock evictionLock = new ReentrantLock();
    private final String name;
    private final Clock clock;
    private final int maxFailedAttempts;
    private final Duration lockout;

    /**
     * @param name              qué se limita, para los avisos (p. ej. "inicio de sesión")
     * @param clock             reloj inyectado
     * @param maxFailedAttempts intentos seguidos sin éxito permitidos antes del bloqueo
     * @param lockout           duración del bloqueo y ventana tras la que un contador sin bloqueo vuelve a 0
     */
    public AttemptLimiter(String name, Clock clock, int maxFailedAttempts, Duration lockout) {
        this.name = name;
        this.clock = clock;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockout = lockout;
    }

    public int maxFailedAttempts() {
        return maxFailedAttempts;
    }

    public Duration lockout() {
        return lockout;
    }

    /**
     * Reserva un intento de forma atómica. Si la clave está bloqueada devuelve un permiso denegado sin cambiar nada.
     * Si no, cuenta el intento y, si es el último permitido, deja fijado el bloqueo desde ahora: un éxito posterior
     * ({@link #recordSuccess}) lo levanta.
     */
    public Permit tryAcquire(String key) {
        Instant now = clock.instant();
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

    /** Un intento exitoso reinicia el contador. */
    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    /** {@code true} si la clave está bloqueada ahora. No modifica el contador. */
    public boolean isLocked(String key) {
        Attempts current = attempts.get(key);
        return current != null && current.isLockedAt(clock.instant());
    }

    /** Solo para pruebas: olvida todos los intentos. */
    public void clear() {
        attempts.clear();
        lastSweep.set(Instant.MIN);
    }

    public int trackedKeys() {
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
        log.warn("Se descartaron {} registros de intentos de {} por superar el tope de {}.",
                candidates.size(), name, MAX_ENTRIES);
    }

    /**
     * Resultado de {@link #tryAcquire}.
     *
     * @param key            clave del intento
     * @param allowed        {@code false} si la clave está bloqueada y el intento no cuenta
     * @param locksOnFailure {@code true} si es el último intento permitido: si falla, queda bloqueada
     */
    public record Permit(String key, boolean allowed, boolean locksOnFailure) {
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
