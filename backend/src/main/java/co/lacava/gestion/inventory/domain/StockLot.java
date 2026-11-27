package co.lacava.gestion.inventory.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Lote de un producto con su vencimiento. Solo {@code InventoryService} lo crea o modifica. */
@Entity
@Table(name = "stock_lot")
public class StockLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity_received", nullable = false)
    private int quantityReceived;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Column(name = "season_name", length = 40)
    private String seasonName;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Version
    private long version;

    protected StockLot() {
    }

    public StockLot(Long productId, int quantity, LocalDate expiresOn, String seasonName, Instant receivedAt,
            Long createdBy) {
        this.productId = productId;
        this.quantityReceived = quantity;
        this.quantityAvailable = quantity;
        this.expiresOn = expiresOn;
        this.seasonName = seasonName;
        this.receivedAt = receivedAt;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantityReceived() {
        return quantityReceived;
    }

    public int getQuantityAvailable() {
        return quantityAvailable;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public String getSeasonName() {
        return seasonName;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public long getVersion() {
        return version;
    }
}
