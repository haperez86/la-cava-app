package co.lacava.gestion.tables.domain;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Mesa del restaurante con su token de QR (HMAC) y PIN de respaldo (D12, D35). */
@Entity
@Table(name = "dining_table")
public class DiningTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer number;

    @Column(name = "qr_token", nullable = false, unique = true, length = 64)
    private String qrToken;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 4)
    private String pin;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "token_rotated_at", nullable = false)
    private Instant tokenRotatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DiningTable() {
    }

    public DiningTable(int number, String qrToken, String pin, Instant now) {
        this.number = number;
        this.qrToken = qrToken;
        this.pin = pin;
        this.active = true;
        this.tokenRotatedAt = now;
        this.createdAt = now;
    }

    /** Nuevo token y PIN: el QR y el PIN anteriores dejan de servir (CA3). */
    public void rotate(String newToken, String newPin, Instant now) {
        this.qrToken = newToken;
        this.pin = newPin;
        this.tokenRotatedAt = now;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public Integer getNumber() {
        return number;
    }

    public String getQrToken() {
        return qrToken;
    }

    public String getPin() {
        return pin;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getTokenRotatedAt() {
        return tokenRotatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Nunca incluye el token ni el PIN. */
    @Override
    public String toString() {
        return "DiningTable[id=" + id + ", number=" + number + ", active=" + active + "]";
    }
}
