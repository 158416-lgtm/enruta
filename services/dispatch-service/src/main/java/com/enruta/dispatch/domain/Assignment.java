package com.enruta.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Asignacion activa de un envio a un conductor.
 *
 * <p>El indice unico parcial {@code uq_assignment_active} es la ultima linea de defensa contra la
 * doble asignacion: aunque fallaran el lock de Redis y la actualizacion condicional, la base
 * rechaza la segunda fila activa.
 */
@Entity
@Table(name = "assignments")
public class Assignment {

    public enum Status {
        ACTIVE,
        CANCELLED,
        COMPLETED
    }

    @Id private UUID id;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected Assignment() {}

    public Assignment(UUID shipmentId, UUID driverId) {
        this.id = UUID.randomUUID();
        this.shipmentId = shipmentId;
        this.driverId = driverId;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
    }

    public void cancel() {
        this.status = Status.CANCELLED;
        this.endedAt = Instant.now();
    }

    public void complete() {
        this.status = Status.COMPLETED;
        this.endedAt = Instant.now();
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

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
