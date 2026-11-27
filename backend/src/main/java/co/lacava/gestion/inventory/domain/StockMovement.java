package co.lacava.gestion.inventory.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Movimiento de existencias (cantidad con signo). Se registra en la misma transacción que el cambio del lote. */
@Entity
@Table(name = "stock_movement")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "lot_id")
    private Long lotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MovementType type;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 140)
    private String note;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(Long productId, Long lotId, MovementType type, int quantity, String note, Long orderId,
            Long userId, Instant createdAt) {
        this.productId = productId;
        this.lotId = lotId;
        this.type = type;
        this.quantity = quantity;
        this.note = note;
        this.orderId = orderId;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getLotId() {
        return lotId;
    }

    public MovementType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getNote() {
        return note;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
