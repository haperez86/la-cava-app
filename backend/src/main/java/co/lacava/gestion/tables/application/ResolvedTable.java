package co.lacava.gestion.tables.application;

import java.time.Instant;

/**
 * Respuesta de {@code POST /tables/resolve}: la mesa, su cuenta abierta y el comprobante de mesa (D37) que el mesero
 * envía como {@code tableCode} al crear un pedido.
 */
public record ResolvedTable(Long id, int number, TableAccount.Account account, String tablePass,
        Instant passExpiresAt) {

    /** No expone el comprobante. */
    @Override
    public String toString() {
        return "ResolvedTable[id=" + id + ", number=" + number + "]";
    }
}
