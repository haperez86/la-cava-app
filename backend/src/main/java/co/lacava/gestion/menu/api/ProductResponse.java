package co.lacava.gestion.menu.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import co.lacava.gestion.menu.application.ProductView;

/** Producto en la API. {@code available} es la existencia vigente (lotes con vencimiento {@code >= hoy}). */
public record ProductResponse(
        Long id,
        String name,
        Long categoryId,
        String categoryName,
        BigDecimal price,
        String description,
        String icon,
        int minStock,
        boolean active,
        String seasonName,
        LocalDate seasonUntil,
        int available) {

    static ProductResponse from(ProductView view) {
        return new ProductResponse(view.id(), view.name(), view.categoryId(), view.categoryName(), view.price(),
                view.description(), view.icon(), view.minStock(), view.active(), view.seasonName(),
                view.seasonUntil(), view.available());
    }
}
