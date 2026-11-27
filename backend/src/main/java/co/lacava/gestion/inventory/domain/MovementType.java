package co.lacava.gestion.inventory.domain;

/** Tipos de movimiento de existencias (docs/02-modelo-datos.md, stock_movement). */
public enum MovementType {
    LOT_IN,
    SALE,
    ORDER_CANCEL_RETURN,
    WASTE_OUT,
    DAMAGED_OUT,
    INTERNAL_USE_OUT,
    ADJUSTMENT_OUT,
    EXPIRED_WRITE_OFF
}
