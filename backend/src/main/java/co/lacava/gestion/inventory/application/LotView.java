package co.lacava.gestion.inventory.application;

import java.time.Instant;
import java.time.LocalDate;

import co.lacava.gestion.inventory.domain.StockLot;

/** Lote con el nombre del producto y su estado calculado con el reloj de negocio. */
public record LotView(
        Long id,
        Long productId,
        String productName,
        int quantityReceived,
        int quantityAvailable,
        LocalDate expiresOn,
        String seasonName,
        Instant receivedAt,
        LotStatus status) {

    static LotView of(StockLot lot, String productName, LocalDate today) {
        return new LotView(lot.getId(), lot.getProductId(), productName, lot.getQuantityReceived(),
                lot.getQuantityAvailable(), lot.getExpiresOn(), lot.getSeasonName(), lot.getReceivedAt(),
                LotStatus.of(lot.getQuantityAvailable(), lot.getExpiresOn(), today));
    }
}
