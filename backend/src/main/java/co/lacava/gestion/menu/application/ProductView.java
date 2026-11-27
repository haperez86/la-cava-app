package co.lacava.gestion.menu.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import co.lacava.gestion.menu.domain.Product;

/** Producto con su existencia vigente, listo para la API (los controllers no exponen entidades). */
public record ProductView(
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

    static ProductView of(Product product, int available) {
        return new ProductView(product.getId(), product.getName(), product.getCategory().getId(),
                product.getCategory().getName(), product.getPrice(), product.getDescription(), product.getIcon(),
                product.getMinStock(), product.isActive(), product.getSeasonName(), product.getSeasonUntil(),
                available);
    }
}
