package co.lacava.gestion.inventory.api;

/**
 * Salida manual (LCV-HU-05). {@code type}: WASTE_OUT, DAMAGED_OUT, INTERNAL_USE_OUT o ADJUSTMENT_OUT. Toda la
 * validación (tipo, observación, cantidad frente a la vigente) la hace {@code InventoryService} para responder los
 * errores juntos.
 */
record RegisterOutputRequest(Long productId, Integer quantity, String type, String note) {
}
