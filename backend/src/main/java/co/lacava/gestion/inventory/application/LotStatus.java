package co.lacava.gestion.inventory.application;

import java.time.LocalDate;

/**
 * Estado de un lote (D32), con prioridad CONSUMED → EXPIRED → EXPIRING → ACTIVE.
 */
public enum LotStatus {
    /** Vigente. */
    ACTIVE,
    /** Vigente, pero vence en 3 días o menos. */
    EXPIRING,
    /** Vencido ({@code expiresOn < hoy}) con cantidad disponible. */
    EXPIRED,
    /** Sin cantidad disponible. */
    CONSUMED;

    /** Días hasta el vencimiento (inclusive) a partir de los cuales un lote vigente "vence pronto". */
    public static final int EXPIRING_DAYS = 3;

    public static LotStatus of(int quantityAvailable, LocalDate expiresOn, LocalDate today) {
        if (quantityAvailable <= 0) {
            return CONSUMED;
        }
        if (expiresOn.isBefore(today)) {
            return EXPIRED;
        }
        if (!expiresOn.isAfter(today.plusDays(EXPIRING_DAYS))) {
            return EXPIRING;
        }
        return ACTIVE;
    }
}
