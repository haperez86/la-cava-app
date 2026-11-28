package co.lacava.gestion.tables.application;

import java.math.BigDecimal;

/**
 * Mesa sin token ni PIN (lo que ven todos los roles) con su estado (LCV-HU-09, §3.3): {@code stage} y
 * {@code waiterName} son {@code null} si está libre; {@code openOrders} y {@code total} cuentan los pedidos abiertos.
 */
public record TableView(Long id, int number, boolean active, TableStatus status, TableStage stage, int openOrders,
        BigDecimal total, String waiterName) {

    static TableView of(Long id, int number, boolean active, TableOccupancy occupancy) {
        if (occupancy == null) {
            return new TableView(id, number, active, TableStatus.FREE, null, 0, BigDecimal.ZERO, null);
        }
        return new TableView(id, number, active, TableStatus.OCCUPIED, occupancy.stage(), occupancy.openOrders(),
                occupancy.total(), occupancy.waiterName());
    }
}
