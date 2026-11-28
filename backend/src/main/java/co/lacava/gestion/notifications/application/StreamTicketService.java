package co.lacava.gestion.notifications.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import co.lacava.gestion.shared.error.BusinessException;
import co.lacava.gestion.shared.error.Problems;
import co.lacava.gestion.users.domain.AppUser;
import co.lacava.gestion.users.infrastructure.AppUserRepository;

/**
 * Tickets de un solo uso y 60 s para abrir el stream SSE (D7, D41): {@code EventSource} no envía headers. Viven en
 * memoria de una sola instancia (D9); la vigencia usa el {@link Clock} inyectado.
 */
@Service
public class StreamTicketService {

    public static final Duration TTL = Duration.ofSeconds(60);
    private static final int TICKET_BYTES = 32;

    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final AppUserRepository users;

    public StreamTicketService(Clock clock, AppUserRepository users) {
        this.clock = clock;
        this.users = users;
    }

    public String issue(long userId) {
        Instant now = clock.instant();
        tickets.values().removeIf(e -> !e.expiresAt.isAfter(now));
        byte[] bytes = new byte[TICKET_BYTES];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Entry(userId, now.plus(TTL)));
        return ticket;
    }

    /**
     * Consume el ticket (queda inservible aunque falle) y devuelve el usuario.
     *
     * @throws BusinessException 401 si es inexistente, reutilizado, vencido o de un usuario ya inactivo
     */
    public long consume(String ticket) {
        Instant now = clock.instant();
        tickets.values().removeIf(e -> !e.expiresAt.isAfter(now));
        Entry entry = ticket == null ? null : tickets.remove(ticket);
        if (entry == null || !entry.expiresAt.isAfter(now)
                || !users.findById(entry.userId).filter(AppUser::isActive).isPresent()) {
            throw BusinessException.unauthorized(Problems.SESSION_EXPIRED);
        }
        return entry.userId;
    }

    private record Entry(long userId, Instant expiresAt) {
    }
}
