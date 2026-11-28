package co.lacava.gestion.tables.application;

import java.math.BigDecimal;

/**
 * Resumen de los pedidos abiertos de una mesa ocupada (LCV-HU-09): cantidad, total, etapa más urgente y mesero del
 * pedido abierto más reciente.
 */
public record TableOccupancy(long tableId, int openOrders, BigDecimal total, TableStage stage, String waiterName) {
}
