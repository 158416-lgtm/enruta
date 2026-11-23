package com.enruta.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Oferta enviada a un conductor concreto, con vencimiento. */
@Entity
@Table(name = "offers")
public class Offer {

    @Id private UUID id;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OfferStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "round", nullable = false)
    private short round;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "score", nullable = false)
    private double score;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Offer() {}

    public Offer(
            UUID shipmentId,
            UUID driverId,
            short round,
            int distanceMeters,
            double score,
            Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.shipmentId = shipmentId;
        this.driverId = driverId;
        this.status = OfferStatus.CREATED;
        this.round = round;
        this.distanceMeters = distanceMeters;
        this.score = score;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public void accept() {
        this.status = OfferStatus.ACCEPTED;
    }

    public void expire() {
        this.status = OfferStatus.EXPIRED;
    }

    public void reject() {
        this.status = OfferStatus.REJECTED;
    }

    public void supersede() {
        this.status = OfferStatus.SUPERSEDED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getShipmentId() {
        return shipmentId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public short getRound() {
        return round;
    }

    public Integer getDistanceMeters() {
        return distanceMeters;
    }

    public double getScore() {
        return score;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setStatus(OfferStatus status) {
        this.status = status;
    }
}
