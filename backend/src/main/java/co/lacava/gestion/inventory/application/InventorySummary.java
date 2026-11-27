package co.lacava.gestion.inventory.application;

import java.util.List;

/** Contadores de alertas de la vista de inventario (LCV-HU-06 CA1) y temporadas para el filtro (D34). */
public record InventorySummary(long outOfStock, long lowStock, long expiring, long expiredUnits,
        List<String> seasons) {
}
