package co.lacava.gestion.orders.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Pedido de una mesa. Nace en {@code PENDING}; su estado solo cambia con {@link OrderStateMachine}. El número visible
 * del pedido es su {@code id}.
 */
@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "waiter_id", nullable = false)
    private Long waiterId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(length = 140)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 120)
    private String cancelReason;

    @Column(name = "bill_id")
    private Long billId;

    @Version
    private long version;

    protected CustomerOrder() {
    }

    /** Pedido nuevo en {@code PENDING}. */
    public CustomerOrder(Long tableId, Long waiterId, Long createdBy, String note, Instant createdAt) {
        this.tableId = tableId;
        this.waiterId = waiterId;
        this.createdBy = createdBy;
        this.note = note;
        this.createdAt = createdAt;
        this.status = OrderStatus.PENDING;
    }

    /** Solo {@link OrderStateMachine}: aplica un estado ya validado y su marca de tiempo. */
    void applyStatus(OrderStatus to, Instant now, String reason) {
        this.status = to;
        switch (to) {
            case IN_PREPARATION -> startedAt = now;
            case READY -> readyAt = now;
            case DELIVERED -> deliveredAt = now;
            case CLOSED -> closedAt = now;
            case CANCELLED -> {
                cancelledAt = now;
                cancelReason = reason;
            }
            case PENDING -> {
                // Estado inicial: no se llega por transición.
            }
        }
    }

    public Long getId() {
        return id;
    }

    public Long getTableId() {
        return tableId;
    }

    public Long getWaiterId() {
        return waiterId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Long getBillId() {
        return billId;
    }

    public long getVersion() {
        return version;
    }
}
