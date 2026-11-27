package co.lacava.gestion.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio con un mensaje en español para el usuario.
 * Se traduce a {@code ProblemDetail} con el código HTTP indicado.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** 409: la petición choca con el estado actual (duplicados, caja abierta, venta anulada…). */
    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    /** 422: los datos enviados violan una regla de negocio (stock insuficiente, nota faltante…). */
    public static BusinessException rule(String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, message);
    }

    /** 404: el recurso no existe. */
    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, message);
    }

    /** 401: credenciales inválidas. */
    public static BusinessException unauthorized(String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
