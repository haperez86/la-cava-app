package co.lacava.gestion.menu.application;

import java.time.LocalDate;

/** Lote inicial opcional al crear un producto (CA4). Sin cantidad no se crea lote. */
public record InitialLot(Integer quantity, LocalDate expiresOn) {
}
