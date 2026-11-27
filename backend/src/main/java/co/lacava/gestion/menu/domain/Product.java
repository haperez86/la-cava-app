package co.lacava.gestion.menu.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Producto vendible de la carta, con temporada opcional (nombre y fecha "disponible hasta"). */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(length = 120)
    private String description;

    @Column(length = 16)
    private String icon;

    @Column(name = "min_stock", nullable = false)
    private int minStock;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "season_name", length = 40)
    private String seasonName;

    @Column(name = "season_until")
    private LocalDate seasonUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(Instant createdAt) {
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** Reemplaza los datos editables del producto. */
    public void update(String name, Category category, BigDecimal price, String description, String icon,
            int minStock, String seasonName, LocalDate seasonUntil, Instant now) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.description = description;
        this.icon = icon;
        this.minStock = minStock;
        this.seasonName = seasonName;
        this.seasonUntil = seasonUntil;
        this.updatedAt = now;
    }

    public void setActive(boolean active, Instant now) {
        this.active = active;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public int getMinStock() {
        return minStock;
    }

    public boolean isActive() {
        return active;
    }

    public String getSeasonName() {
        return seasonName;
    }

    public LocalDate getSeasonUntil() {
        return seasonUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
