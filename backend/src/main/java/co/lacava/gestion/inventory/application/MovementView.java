package co.lacava.gestion.inventory.application;

import java.time.Instant;

import co.lacava.gestion.inventory.domain.MovementType;

/** Movimiento de existencias con los nombres del producto y del usuario (LCV-HU-06 CA4). */
public record MovementView(
        Long id,
        Instant createdAt,
        Long productId,
        String productName,
        Long lotId,
        MovementType type,
        int quantity,
        String note,
        Long orderId,
        String userName) {
}
