package co.lacava.gestion.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.lacava.gestion.shared.error.BusinessException;

/** Endpoints que solo existen en pruebas para verificar la traducción de errores a ProblemDetail. */
@RestController
@Profile("test")
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/v1/test/errors")
class ErrorProbeController {

    record ProbeRequest(
            @NotBlank(message = "El nombre es obligatorio.") String name,
            @Positive(message = "La cantidad debe ser mayor que 0.") int quantity) {
    }

    @PostMapping("/validation")
    void validation(@Valid @RequestBody ProbeRequest request) {
    }

    @GetMapping("/not-found")
    void notFound() {
        throw BusinessException.notFound("No existe el producto 99.");
    }

    @GetMapping("/conflict")
    void conflict() {
        throw BusinessException.conflict("Ya hay un turno de caja abierto.");
    }

    @GetMapping("/rule")
    void rule() {
        throw BusinessException.rule("No hay existencias suficientes.");
    }

    @GetMapping("/optimistic-lock")
    void optimisticLock() {
        throw new org.springframework.orm.ObjectOptimisticLockingFailureException("Product", 1L);
    }

    @GetMapping("/integrity")
    void integrity() {
        throw new org.springframework.dao.DataIntegrityViolationException("duplicate key uk_product_name");
    }

    @GetMapping("/unexpected")
    void unexpected() {
        throw new IllegalStateException("fallo interno con detalles técnicos");
    }
}
