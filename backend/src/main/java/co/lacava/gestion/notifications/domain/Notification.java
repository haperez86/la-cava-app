package co.lacava.gestion.notifications.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Aviso para un usuario (D42). Se crea sin leer y se marca leído una sola vez. */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private NotificationType type;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "table_number", nullable = false, updatable = false)
    private int tableNumber;

    @Column(nullable = false, length = 160, updatable = false)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(Long userId, NotificationType type, Long orderId, int tableNumber, String message,
            Instant createdAt) {
        this.userId = userId;
        this.type = type;
        this.orderId = orderId;
        this.tableNumber = tableNumber;
        this.message = message;
        this.createdAt = createdAt;
    }

    /** Marca leído; si ya lo estaba conserva la primera marca. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public Long getOrderId() {
        return orderId;
    }

    public int getTableNumber() {
        return tableNumber;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
