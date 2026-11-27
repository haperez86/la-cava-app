package co.lacava.gestion.tables.application;

import java.math.BigDecimal;
import java.util.List;

/** Mesa con su cuenta abierta. Hasta la Épica 4 la cuenta no tiene pedidos y su total es 0 (D35). */
public record TableAccount(Long id, int number, Account account) {

    public record Account(List<Object> orders, BigDecimal total) {

        public static Account empty() {
            return new Account(List.of(), BigDecimal.ZERO);
        }
    }
}
