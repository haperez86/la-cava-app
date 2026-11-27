package co.lacava.gestion.support;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints que solo existen en pruebas para verificar la autorización por rol (LCV-HU-01 CA4). */
@RestController
@Profile("test")
@RequestMapping("/api/v1/test/roles")
class RoleProbeController {

    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    String adminOnly() {
        return "ok";
    }

    @GetMapping("/cashier-only")
    @PreAuthorize("hasRole('CASHIER')")
    String cashierOnly() {
        return "ok";
    }
}
