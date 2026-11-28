package co.lacava.gestion.orders.api;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.lacava.gestion.orders.application.OrderQueryService;
import co.lacava.gestion.orders.application.OrderService;
import co.lacava.gestion.orders.application.OrderSummary;
import co.lacava.gestion.orders.application.OrderView;
import co.lacava.gestion.shared.api.PageResponse;
import co.lacava.gestion.shared.security.CurrentUser;
import co.lacava.gestion.users.domain.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** LCV-HU-10: crear y consultar pedidos. */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Pedidos")
class OrderController {

    private final OrderService orderService;

    private final OrderQueryService queries;

    OrderController(OrderService orderService, OrderQueryService queries) {
        this.orderService = orderService;
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    @Operation(summary = "Pedidos del más reciente al más antiguo; filtros status, q (número de pedido o mesa si es "
            + "numérico, mesero si es texto) y fechas from/to; paginado")
    PageResponse<OrderView> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(queries.search(status, q, from, to, pageable), order -> order);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    @Operation(summary = "Contadores por estado (all, PENDING, ...) con q/from/to e ignorando status")
    OrderSummary summary(@RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return queries.summary(q, from, to);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    @Operation(summary = "Cancela un pedido PENDING con motivo (1-120) y devuelve el stock a sus lotes; otro estado: 409")
    OrderView cancel(@PathVariable long id, @RequestBody CancelOrderRequest request, @AuthenticationPrincipal Jwt jwt) {
        return orderService.cancel(id, request.reason(), CurrentUser.id(jwt));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'KITCHEN')")
    @Operation(summary = "Inicia la preparación: PENDING → IN_PREPARATION con startedAt; otro estado: 409")
    OrderView start(@PathVariable long id) {
        return orderService.start(id);
    }

    @PostMapping("/{id}/ready")
    @PreAuthorize("hasAnyRole('ADMIN', 'KITCHEN')")
    @Operation(summary = "Marca el pedido listo: IN_PREPARATION → READY con readyAt; otro estado: 409")
    OrderView ready(@PathVariable long id) {
        return orderService.ready(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER', 'WAITER')")
    @Operation(summary = "Crea un pedido PENDING consumiendo lotes en FEFO; mesero: tableCode = tablePass de resolve; "
            + "422 si un producto no está disponible o no alcanza")
    OrderView create(@RequestBody CreateOrderRequest request, @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        return orderService.create(request.toNewOrder(), CurrentUser.id(jwt), roleOf(authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Pedido con ítems y total; el mesero solo los suyos (404 si es ajeno)")
    OrderView get(@PathVariable long id, @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        return orderService.get(id, CurrentUser.id(jwt), roleOf(authentication));
    }

    private static Role roleOf(Authentication authentication) {
        for (Role role : Role.values()) {
            if (CurrentUser.hasRole(authentication, role.name())) {
                return role;
            }
        }
        throw new org.springframework.security.access.AccessDeniedException("Sin rol");
    }
}
