package co.lacava.gestion.orders.domain;

import java.io.Serializable;
import java.util.Objects;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/** Cantidad que un ítem tomó de un lote (FEFO); permite devolverla al lote exacto si se cancela (D11). */
@Entity
@Table(name = "order_item_lot")
public class OrderItemLot implements Persistable<OrderItemLot.Key> {

    @EmbeddedId
    private Key id;

    @Column(nullable = false, updatable = false)
    private int quantity;

    /** La PK es asignada: sin esto, {@code save} haría un SELECT previo (merge) por cada fila. */
    @Transient
    private boolean fresh = true;

    protected OrderItemLot() {
    }

    public OrderItemLot(Long orderItemId, Long lotId, int quantity) {
        this.id = new Key(orderItemId, lotId);
        this.quantity = quantity;
    }

    @Override
    public Key getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        fresh = false;
    }

    public Long getOrderItemId() {
        return id.orderItemId;
    }

    public Long getLotId() {
        return id.lotId;
    }

    public int getQuantity() {
        return quantity;
    }

    /** PK compuesta {@code (order_item_id, lot_id)}. */
    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "order_item_id", nullable = false)
        private Long orderItemId;

        @Column(name = "lot_id", nullable = false)
        private Long lotId;

        protected Key() {
        }

        public Key(Long orderItemId, Long lotId) {
            this.orderItemId = orderItemId;
            this.lotId = lotId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(orderItemId, key.orderItemId)
                    && Objects.equals(lotId, key.lotId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(orderItemId, lotId);
        }
    }
}
