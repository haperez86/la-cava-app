package co.lacava.gestion.users.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.lacava.gestion.users.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Usuarios. En LCV-HU-10 solo se adelanta {@code GET /users/waiters} de la Épica 8 (D38). */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Usuarios")
class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/waiters")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    @Operation(summary = "Meseros activos ordenados por nombre, para asignar pedidos")
    List<WaiterResponse> waiters() {
        return userService.activeWaiters().stream().map(WaiterResponse::from).toList();
    }
}
