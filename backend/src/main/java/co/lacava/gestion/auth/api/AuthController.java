package co.lacava.gestion.auth.api;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.lacava.gestion.auth.application.AuthService;
import co.lacava.gestion.auth.application.AuthService.LoginResult;
import co.lacava.gestion.users.domain.AppUser;
import co.lacava.gestion.users.domain.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Inicia sesión y devuelve el JWT (público)")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request.username(), request.password());
        return new LoginResponse(result.token().token(), result.token().expiresAt(), SessionUser.from(result.user()));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER', 'WAITER', 'KITCHEN')")
    @Operation(summary = "Usuario de la sesión actual")
    SessionUser me(@AuthenticationPrincipal Jwt jwt) {
        return SessionUser.from(authService.currentUser(Long.parseLong(jwt.getSubject())));
    }

    record LoginRequest(
            @NotBlank(message = "El usuario es obligatorio.") @Size(max = 40, message = "El usuario admite máximo 40 caracteres.") String username,
            @NotBlank(message = "La contraseña es obligatoria.") @Size(max = 100, message = "La contraseña admite máximo 100 caracteres.") String password) {
    }

    record LoginResponse(String token, Instant expiresAt, SessionUser user) {
    }

    record SessionUser(Long id, String fullName, Role role) {

        static SessionUser from(AppUser user) {
            return new SessionUser(user.getId(), user.getFullName(), user.getRole());
        }
    }
}
