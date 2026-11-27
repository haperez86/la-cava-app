package co.lacava.gestion.inventory.application;

import java.util.List;

import co.lacava.gestion.inventory.domain.MovementType;

/** Resultado de una salida manual: lotes consumidos y existencia vigente que queda. */
public record OutputView(
        Long productId,
        String productName,
        MovementType type,
        int quantity,
        List<LotAllocation> consumed,
        int available) {
}
