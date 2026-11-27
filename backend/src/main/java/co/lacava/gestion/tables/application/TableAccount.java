package co.lacava.gestion.tables.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mesa con su cuenta abierta: pedidos {@code PENDING}, {@code IN_PREPARATION}, {@code READY} y {@code DELIVERED}
 * con sus ítems, y el total (D38). Los pedidos los arma el módulo de pedidos ({@link OpenTableOrders}).
 */
public record TableAccount(Long id, int number, Account account) {

    /** {@code orders} son {@code OrderView} del módulo de pedidos; aquí se tratan como datos opacos. */
    public record Account(List<?> orders, BigDecimal total) {

        public static Account empty() {
            return new Account(List.of(), BigDecimal.ZERO);
        }
    }
}
