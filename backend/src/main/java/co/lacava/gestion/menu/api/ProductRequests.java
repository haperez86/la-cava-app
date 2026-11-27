package co.lacava.gestion.menu.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import co.lacava.gestion.menu.application.InitialLot;
import co.lacava.gestion.menu.application.ProductData;

/** Cuerpos de petición del menú. Las reglas entre campos (CA2, CA4) las valida {@code ProductService}. */
final class ProductRequests {

    static final String NAME_REQUIRED = "El nombre es obligatorio.";
    static final String NAME_SIZE = "El nombre admite máximo 60 caracteres.";
    static final String CATEGORY_REQUIRED = "La categoría es obligatoria.";
    static final String CATEGORY_SIZE = "La categoría admite máximo 40 caracteres.";
    static final String PRICE_REQUIRED = "El precio es obligatorio.";
    static final String PRICE_POSITIVE = "El precio debe ser mayor que 0.";
    static final String PRICE_DIGITS = "El precio admite hasta 12 enteros y 2 decimales.";
    static final String DESCRIPTION_SIZE = "La descripción admite máximo 120 caracteres.";
    static final String ICON_SIZE = "El ícono admite máximo 16 caracteres.";
    static final String MIN_STOCK = "El stock mínimo no puede ser negativo.";
    static final String SEASON_SIZE = "El nombre de la temporada admite máximo 40 caracteres.";
    static final String QUANTITY_POSITIVE = "La cantidad debe ser mayor que 0.";

    private ProductRequests() {
    }

    /** Alta: datos del producto y lote inicial opcional. */
    record CreateProductRequest(
            @NotBlank(message = NAME_REQUIRED) @Size(max = 60, message = NAME_SIZE) String name,
            @NotBlank(message = CATEGORY_REQUIRED) @Size(max = 40, message = CATEGORY_SIZE) String categoryName,
            @NotNull(message = PRICE_REQUIRED) @Positive(message = PRICE_POSITIVE)
            @Digits(integer = 12, fraction = 2, message = PRICE_DIGITS) BigDecimal price,
            @Size(max = 120, message = DESCRIPTION_SIZE) String description,
            @Size(max = 16, message = ICON_SIZE) String icon,
            @PositiveOrZero(message = MIN_STOCK) Integer minStock,
            @Size(max = 40, message = SEASON_SIZE) String seasonName,
            LocalDate seasonUntil,
            @Valid InitialLotRequest initialLot) {

        ProductData data() {
            return new ProductData(name, categoryName, price, description, icon, minStock, seasonName, seasonUntil);
        }

        InitialLot lot() {
            return initialLot == null ? null : new InitialLot(initialLot.quantity(), initialLot.expiresOn());
        }
    }

    /** Edición: los mismos datos, sin lote inicial. */
    record UpdateProductRequest(
            @NotBlank(message = NAME_REQUIRED) @Size(max = 60, message = NAME_SIZE) String name,
            @NotBlank(message = CATEGORY_REQUIRED) @Size(max = 40, message = CATEGORY_SIZE) String categoryName,
            @NotNull(message = PRICE_REQUIRED) @Positive(message = PRICE_POSITIVE)
            @Digits(integer = 12, fraction = 2, message = PRICE_DIGITS) BigDecimal price,
            @Size(max = 120, message = DESCRIPTION_SIZE) String description,
            @Size(max = 16, message = ICON_SIZE) String icon,
            @PositiveOrZero(message = MIN_STOCK) Integer minStock,
            @Size(max = 40, message = SEASON_SIZE) String seasonName,
            LocalDate seasonUntil) {

        ProductData data() {
            return new ProductData(name, categoryName, price, description, icon, minStock, seasonName, seasonUntil);
        }
    }

    record InitialLotRequest(@Positive(message = QUANTITY_POSITIVE) Integer quantity, LocalDate expiresOn) {
    }

    record ActiveRequest(@NotNull(message = "Indica si el producto queda activo.") Boolean active) {
    }
}
