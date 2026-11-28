package co.lacava.gestion.tables.application;

import java.util.Map;

/**
 * Puerto hacia el módulo de pedidos: la cuenta abierta de una mesa y el resumen de ocupación. Lo implementa
 * {@code orders}, que ya depende de {@code tables}; así no hay dependencia circular entre los dos módulos.
 */
public interface OpenTableOrders {

    /** Pedidos abiertos de la mesa con ítems y total (vacía si no tiene). */
    TableAccount.Account openAccount(long tableId);

    /** Resumen por id de mesa de las mesas con pedidos abiertos (las libres no aparecen), en una sola consulta. */
    Map<Long, TableOccupancy> occupancyByTable();
}
