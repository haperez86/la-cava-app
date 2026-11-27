package co.lacava.gestion.shared.error;

import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Títulos y mensajes en español para las respuestas {@code ProblemDetail}. */
public final class Problems {

    public static final String UNAUTHENTICATED = "Debes iniciar sesión para continuar.";
    public static final String SESSION_EXPIRED = "Tu sesión expiró o no es válida. Inicia sesión de nuevo.";
    public static final String FORBIDDEN = "No tienes permiso para realizar esta acción.";
    public static final String VALIDATION = "Revisa los datos enviados.";
    public static final String UNREADABLE = "El cuerpo de la petición no es válido.";
    public static final String NOT_FOUND = "No se encontró el recurso solicitado.";
    public static final String CONCURRENT_UPDATE = "Otro usuario modificó estos datos al mismo tiempo. Recarga e intenta de nuevo.";
    public static final String DATA_CONFLICT = "Los datos entran en conflicto con un registro existente (por ejemplo, un nombre o un código repetido).";
    public static final String INTERNAL = "Ocurrió un error inesperado. Intenta de nuevo.";

    private static final Map<Integer, String> TITLES = Map.ofEntries(
            Map.entry(400, "Datos inválidos"),
            Map.entry(401, "No autenticado"),
            Map.entry(403, "Acceso denegado"),
            Map.entry(404, "No encontrado"),
            Map.entry(405, "Método no permitido"),
            Map.entry(409, "Conflicto"),
            Map.entry(415, "Tipo de contenido no soportado"),
            Map.entry(422, "Regla de negocio"),
            Map.entry(500, "Error interno"),
            Map.entry(503, "Servicio no disponible"));

    private static final Map<Integer, String> GENERIC_DETAILS = Map.of(
            400, VALIDATION,
            401, UNAUTHENTICATED,
            403, FORBIDDEN,
            404, NOT_FOUND,
            405, "El método HTTP no está permitido para este recurso.",
            415, "El tipo de contenido no está soportado.",
            500, INTERNAL);

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title(status));
        return problem;
    }

    public static String title(HttpStatusCode status) {
        return TITLES.getOrDefault(status.value(), "Error");
    }

    public static String genericDetail(HttpStatusCode status) {
        return GENERIC_DETAILS.getOrDefault(status.value(), INTERNAL);
    }
}
