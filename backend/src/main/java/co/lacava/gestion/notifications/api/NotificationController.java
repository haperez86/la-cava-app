package co.lacava.gestion.notifications.api;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import co.lacava.gestion.notifications.application.NotificationService;
import co.lacava.gestion.notifications.application.NotificationView;
import co.lacava.gestion.notifications.application.SseHub;
import co.lacava.gestion.notifications.application.StreamTicketService;
import co.lacava.gestion.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** LCV-HU-13: ticket y canal SSE (D7, D41). */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notificaciones")
class NotificationController {

    private final StreamTicketService tickets;
    private final SseHub hub;
    private final NotificationService notifications;

    NotificationController(StreamTicketService tickets, SseHub hub, NotificationService notifications) {
        this.tickets = tickets;
        this.hub = hub;
        this.notifications = notifications;
    }

    @PostMapping("/stream-ticket")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER', 'WAITER', 'KITCHEN')")
    @Operation(summary = "Ticket de un solo uso y 60 s para abrir el stream SSE")
    Map<String, String> streamTicket(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("ticket", tickets.issue(CurrentUser.id(jwt)));
    }

    /** Público en {@code SecurityConfig}: lo autoriza el ticket (vencido, reutilizado o inexistente → 401). */
    @GetMapping("/stream")
    @SecurityRequirements
    @Operation(summary = "Canal text/event-stream con eventos order-changed y heartbeat; se autentica con ?ticket=")
    SseEmitter stream(@RequestParam(required = false) String ticket) {
        long userId = tickets.consume(ticket);
        return hub.register(userId);
    }

    @GetMapping("/unread")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER', 'WAITER', 'KITCHEN')")
    @Operation(summary = "Avisos no leídos del usuario autenticado, del más antiguo al más reciente")
    List<NotificationView> unread(@AuthenticationPrincipal Jwt jwt) {
        return notifications.unread(CurrentUser.id(jwt));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER', 'WAITER', 'KITCHEN')")
    @Operation(summary = "Marca leído un aviso propio (ajeno o inexistente: 404; ya leído: sin error)")
    void read(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        notifications.markRead(id, CurrentUser.id(jwt));
    }
}
