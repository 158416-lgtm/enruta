package com.enruta.shipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Bitacora de estados. Es la fuente del timeline que se muestra en la interfaz. */
@Entity
@Table(name = "shipment_status_history")
public class ShipmentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 24)
    private ShipmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 24)
    private ShipmentStatus toStatus;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(length = 300)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ShipmentStatusHistory() {}

    public ShipmentStatusHistory(
            UUID shipmentId,
            ShipmentStatus fromStatus,
            ShipmentStatus toStatus,
            UUID actorId,
            String reason) {
        this.shipmentId = shipmentId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.reason = reason;
        this.occurredAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getShipmentId() {
        return shipmentId;
    }

    public ShipmentStatus getFromStatus() {
        return fromStatus;
    }

    public ShipmentStatus getToStatus() {
        return toStatus;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
