package com.enruta.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Estado del proceso de asignacion de un envio (una fila por envio). */
@Entity
@Table(name = "dispatch_state")
public class DispatchState {

    public enum Phase {
        SEARCHING,
        OFFERED,
        ASSIGNED,
        COMPLETED,
        EXHAUSTED,
        CANCELLED
    }

    @Id
    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "pickup_lat", nullable = false)
    private double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private double pickupLng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Phase phase;

    @Column(name = "round", nullable = false)
    private short round;

    @Column(name = "current_offer_id")
    private UUID currentOfferId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DispatchState() {}

    public DispatchState(UUID shipmentId, double pickupLat, double pickupLng) {
        this.shipmentId = shipmentId;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.phase = Phase.SEARCHING;
        this.round = 0;
        this.updatedAt = Instant.now();
    }

    public void nextRound() {
        this.round = (short) (this.round + 1);
        this.phase = Phase.SEARCHING;
        this.currentOfferId = null;
        this.updatedAt = Instant.now();
    }

    public void markOffered(UUID offerId) {
        this.phase = Phase.OFFERED;
        this.currentOfferId = offerId;
        this.updatedAt = Instant.now();
    }

    public void markAssigned() {
        this.phase = Phase.ASSIGNED;
        this.currentOfferId = null;
        this.updatedAt = Instant.now();
    }

    public void markExhausted() {
        this.phase = Phase.EXHAUSTED;
        this.currentOfferId = null;
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        this.phase = Phase.COMPLETED;
        this.currentOfferId = null;
        this.updatedAt = Instant.now();
    }

    public void markCancelled() {
        this.phase = Phase.CANCELLED;
        this.currentOfferId = null;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return phase == Phase.SEARCHING || phase == Phase.OFFERED;
    }

    public UUID getShipmentId() {
        return shipmentId;
    }

    public double getPickupLat() {
        return pickupLat;
    }

    public double getPickupLng() {
        return pickupLng;
    }

    public Phase getPhase() {
        return phase;
    }

    public short getRound() {
        return round;
    }

    public UUID getCurrentOfferId() {
        return currentOfferId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
        this.updatedAt = Instant.now();
    }
}
