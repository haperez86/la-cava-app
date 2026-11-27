package co.lacava.gestion.inventory.application;

import java.time.LocalDate;

/**
 * Estado de un producto en la vista de inventario (LCV-HU-06, D34), con prioridad
 * OUT_OF_SEASON → OUT → LOW → EXPIRING → AVAILABLE.
 */
public enum InventoryState {
    /** Temporada vencida ({@code seasonUntil < hoy}). */
    OUT_OF_SEASON,
    /** En temporada y sin existencia vigente. */
    OUT,
    /** En temporada y {@code 0 < vigente <= mínimo}. */
    LOW,
    /** El próximo lote vigente vence en {@link LotStatus#EXPIRING_DAYS} días o menos. */
    EXPIRING,
    AVAILABLE;

    /**
     * @param daysToExpiry días hasta el vencimiento del próximo lote vigente, o {@code null} si no hay
     */
    public static InventoryState of(LocalDate seasonUntil, int available, int minStock, Integer daysToExpiry,
            LocalDate today) {
        if (seasonUntil != null && seasonUntil.isBefore(today)) {
            return OUT_OF_SEASON;
        }
        if (available <= 0) {
            return OUT;
        }
        if (available <= minStock) {
            return LOW;
        }
        if (daysToExpiry != null && daysToExpiry <= LotStatus.EXPIRING_DAYS) {
            return EXPIRING;
        }
        return AVAILABLE;
    }
}
