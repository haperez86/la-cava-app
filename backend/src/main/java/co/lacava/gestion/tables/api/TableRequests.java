package co.lacava.gestion.tables.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Cuerpos de petición de mesas. */
final class TableRequests {

    static final String NUMBER_REQUIRED = "El número de mesa es obligatorio.";
    static final String NUMBER_POSITIVE = "El número de mesa debe ser mayor que 0.";
    static final String RESOLVE_REQUIRED = "Envía el código del QR o el número de mesa y el PIN.";

    private TableRequests() {
    }

    record CreateTableRequest(
            @NotNull(message = NUMBER_REQUIRED) @Positive(message = NUMBER_POSITIVE) Integer number) {
    }

    record ActiveRequest(@NotNull(message = "Indica si la mesa queda activa.") Boolean active) {
    }

    /** {@code {code}} (contenido del QR) o {@code {number, pin}}. */
    record ResolveRequest(String code, Integer number, String pin) {

        /** No expone el código ni el PIN. */
        @Override
        public String toString() {
            return "ResolveRequest[number=" + number + "]";
        }
    }
}
