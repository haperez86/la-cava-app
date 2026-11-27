package co.lacava.gestion.menu.application;

import java.time.LocalDate;

/**
 * Estado de un producto en la carta (LCV-HU-03). Si se cumplen varios, gana el primero en este orden:
 * retirado, fuera de temporada, agotado, en la carta.
 */
public enum ProductState {

    INACTIVE("Retirado de la carta"),
    OUT_OF_SEASON("Fuera de temporada"),
    OUT_OF_STOCK("Agotado"),
    ON_MENU(null);

    private final String blockReason;

    ProductState(String blockReason) {
        this.blockReason = blockReason;
    }

    /** Motivo por el que no se puede pedir, o {@code null} si el producto es pedible. */
    public String blockReason() {
        return blockReason;
    }

    public boolean orderable() {
        return this == ON_MENU;
    }

    /** Estado de un producto dado "hoy" (zona de negocio) y su existencia vigente. */
    public static ProductState of(boolean active, LocalDate seasonUntil, LocalDate today, int available) {
        if (!active) {
            return INACTIVE;
        }
        if (seasonUntil != null && seasonUntil.isBefore(today)) {
            return OUT_OF_SEASON;
        }
        return available > 0 ? ON_MENU : OUT_OF_STOCK;
    }
}
