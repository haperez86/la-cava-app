package co.lacava.gestion.menu.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import co.lacava.gestion.menu.application.ProductState;
import co.lacava.gestion.menu.application.ProductView;

/**
 * Producto en la API. {@code available} es la existencia vigente (lotes con vencimiento {@code >= hoy}).
 * {@code state} (INACTIVE, OUT_OF_SEASON, OUT_OF_STOCK, ON_MENU), {@code orderable} y {@code blockReason}
 * (nulo si es pedible) se calculan con el reloj de negocio.
 */
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
        int available,
        ProductState state,
        boolean orderable,
        String blockReason) {

    static ProductResponse from(ProductView view) {
        return new ProductResponse(view.id(), view.name(), view.categoryId(), view.categoryName(), view.price(),
                view.description(), view.icon(), view.minStock(), view.active(), view.seasonName(),
                view.seasonUntil(), view.available(), view.state(), view.orderable(), view.blockReason());
    }
}
