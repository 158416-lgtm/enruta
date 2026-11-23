package com.enruta.shipment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ShipmentStatus status;

    @Column(name = "pickup_lat", nullable = false)
    private double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private double pickupLng;

    @Column(name = "dropoff_lat", nullable = false)
    private double dropoffLat;

    @Column(name = "dropoff_lng", nullable = false)
    private double dropoffLng;

    @Column(name = "pickup_address", nullable = false, length = 300)
    private String pickupAddress;

    @Column(name = "dropoff_address", nullable = false, length = 300)
    private String dropoffAddress;

    @Column(name = "pickup_pin", nullable = false, length = 4)
    private String pickupPin;

    @Column(name = "dispatch_rounds", nullable = false)
    private short dispatchRounds;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Shipment() {}

    public Shipment(
            UUID customerId,
            double pickupLat,
            double pickupLng,
            double dropoffLat,
            double dropoffLng,
            String pickupAddress,
            String dropoffAddress,
            String pickupPin) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.status = ShipmentStatus.CREATED;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.dropoffLat = dropoffLat;
        this.dropoffLng = dropoffLng;
        this.pickupAddress = pickupAddress;
        this.dropoffAddress = dropoffAddress;
        this.pickupPin = pickupPin;
        this.dispatchRounds = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Cambia de estado validando la maquina de estados.
     *
     * @throws IllegalStateException si la transicion no esta permitida; el servicio lo traduce a
     *     409
     */
    public void transitionTo(ShipmentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Transicion invalida: " + status + " -> " + next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public void assignTo(UUID driverId) {
        transitionTo(ShipmentStatus.ASSIGNED);
        this.driverId = driverId;
    }

    /** Compensacion: el conductor se cayo antes de recoger; vuelve a estar sin asignar. */
    public void unassign() {
        transitionTo(ShipmentStatus.CREATED);
        this.driverId = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public double getPickupLat() {
        return pickupLat;
    }

    public double getPickupLng() {
        return pickupLng;
    }

    public double getDropoffLat() {
        return dropoffLat;
    }

    public double getDropoffLng() {
        return dropoffLng;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public String getDropoffAddress() {
        return dropoffAddress;
    }

    public String getPickupPin() {
        return pickupPin;
    }

    public short getDispatchRounds() {
        return dispatchRounds;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
