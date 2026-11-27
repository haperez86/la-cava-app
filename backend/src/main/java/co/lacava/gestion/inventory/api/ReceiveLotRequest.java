package co.lacava.gestion.inventory.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Entrada de un lote (LCV-HU-04). El vencimiento {@code >= hoy} lo valida {@code InventoryService} con el Clock. */
record ReceiveLotRequest(
        @NotNull(message = "El producto es obligatorio.") Long productId,
        @NotNull(message = "La cantidad es obligatoria.")
        @Positive(message = "La cantidad debe ser mayor que 0.") Integer quantity,
        @NotNull(message = "La fecha de vencimiento es obligatoria.") LocalDate expiresOn,
        @Size(max = 40, message = "El nombre de la temporada admite máximo 40 caracteres.") String seasonName) {
}
