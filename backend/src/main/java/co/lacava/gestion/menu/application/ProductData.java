package co.lacava.gestion.menu.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos editables de un producto (alta y edición). Los límites de longitud y rangos los valida la API con Bean
 * Validation; {@link ProductService} valida las reglas entre campos (CA2) y normaliza espacios.
 */
public record ProductData(
        String name,
        String categoryName,
        BigDecimal price,
        String description,
        String icon,
        Integer minStock,
        String seasonName,
        LocalDate seasonUntil) {
}
