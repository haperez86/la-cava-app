package co.lacava.gestion.orders.api;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.lacava.gestion.orders.application.OrderService;
import co.lacava.gestion.orders.application.OrderView;
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

    OrderController(OrderService orderService) {
        this.orderService = orderService;
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
