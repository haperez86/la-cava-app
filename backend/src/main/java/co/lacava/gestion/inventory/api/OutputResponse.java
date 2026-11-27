package co.lacava.gestion.inventory.api;

import java.time.LocalDate;
import java.util.List;

import co.lacava.gestion.inventory.application.OutputView;
import co.lacava.gestion.inventory.domain.MovementType;

/** Salida registrada: lotes consumidos en FEFO y existencia vigente que queda ({@code available}). */
public record OutputResponse(
        Long productId,
        String productName,
        MovementType type,
        int quantity,
        List<ConsumedLot> consumed,
        int available) {

    /** Cantidad tomada de un lote. */
    public record ConsumedLot(Long lotId, LocalDate expiresOn, int quantity) {
    }

    static OutputResponse from(OutputView view) {
        return new OutputResponse(view.productId(), view.productName(), view.type(), view.quantity(),
                view.consumed().stream().map(a -> new ConsumedLot(a.lotId(), a.expiresOn(), a.quantity())).toList(),
                view.available());
    }
}
