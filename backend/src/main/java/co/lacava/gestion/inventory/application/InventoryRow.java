package co.lacava.gestion.inventory.application;

import java.time.LocalDate;

/** Fila de la vista de inventario (LCV-HU-06 CA2, D34). */
public record InventoryRow(
        Long productId,
        String name,
        String icon,
        Long categoryId,
        String categoryName,
        String seasonName,
        LocalDate seasonUntil,
        int available,
        int minStock,
        LocalDate nextExpiresOn,
        Integer daysToExpiry,
        int expiredUnits,
        InventoryState state,
        boolean hasExpired) {
}
