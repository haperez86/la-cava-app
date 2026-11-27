package co.lacava.gestion.inventory.application;

/** Resultado de "Dar de baja vencidos": lotes dejados en 0 y unidades dadas de baja. */
public record WriteOffView(int lots, int units) {
}
