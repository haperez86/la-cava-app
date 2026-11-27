package co.lacava.gestion.inventory.api;

import co.lacava.gestion.inventory.application.WriteOffView;

/** Baja de vencidos: lotes dejados en 0 y unidades dadas de baja. */
public record WriteOffResponse(int lots, int units) {

    static WriteOffResponse from(WriteOffView view) {
        return new WriteOffResponse(view.lots(), view.units());
    }
}
