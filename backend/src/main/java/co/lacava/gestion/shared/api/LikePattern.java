package co.lacava.gestion.shared.api;

import java.util.Locale;

/** Patrones {@code LIKE} para búsquedas por texto (escape con {@code \}). */
public final class LikePattern {

    private LikePattern() {
    }

    /** {@code %texto%} en minúsculas, sin espacios a los lados y con los comodines escapados. */
    public static String contains(String text) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
