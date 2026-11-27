package co.lacava.gestion.inventory.application;

import java.time.LocalDate;

/** Cantidad que un consumo FEFO tomó de un lote (la Épica 4 la guarda en {@code order_item_lot}). */
public record LotAllocation(Long lotId, LocalDate expiresOn, int quantity) {
}
