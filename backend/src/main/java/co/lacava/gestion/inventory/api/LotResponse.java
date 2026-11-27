package co.lacava.gestion.inventory.api;

import java.time.Instant;
import java.time.LocalDate;

import co.lacava.gestion.inventory.application.LotStatus;
import co.lacava.gestion.inventory.application.LotView;

/**
 * Lote en la API. {@code status}: ACTIVE, EXPIRING (vence en ≤ 3 días), EXPIRED (vencido con cantidad > 0) o
 * CONSUMED (cantidad 0), calculado con el reloj de negocio.
 */
public record LotResponse(
        Long id,
        Long productId,
        String productName,
        int quantityReceived,
        int quantityAvailable,
        LocalDate expiresOn,
        String seasonName,
        Instant receivedAt,
        LotStatus status) {

    static LotResponse from(LotView view) {
        return new LotResponse(view.id(), view.productId(), view.productName(), view.quantityReceived(),
                view.quantityAvailable(), view.expiresOn(), view.seasonName(), view.receivedAt(), view.status());
    }
}
