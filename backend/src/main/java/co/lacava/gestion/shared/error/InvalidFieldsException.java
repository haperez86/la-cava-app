package co.lacava.gestion.shared.error;

import java.util.List;

/**
 * Validación de campos que no expresa Bean Validation (reglas entre campos o que dependen del {@code Clock}).
 * Se traduce a 400 con {@code errors[]}, igual que una validación de Bean Validation.
 */
public class InvalidFieldsException extends RuntimeException {

    private final List<FieldError> errors;

    public InvalidFieldsException(List<FieldError> errors) {
        super(Problems.VALIDATION);
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
