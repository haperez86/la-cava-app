package co.lacava.gestion.tables.application;

/**
 * Etapa más urgente de una mesa ocupada (especificación §3.3), de mayor a menor urgencia: el orden de declaración es
 * la prioridad. {@code SERVED} = todos sus pedidos abiertos están entregados.
 */
public enum TableStage {
    READY,
    IN_PREPARATION,
    PENDING,
    SERVED
}
