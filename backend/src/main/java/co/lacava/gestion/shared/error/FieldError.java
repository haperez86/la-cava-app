package co.lacava.gestion.shared.error;

/** Elemento de {@code errors[]} en un {@code ProblemDetail} de validación. */
public record FieldError(String field, String message) {
}
