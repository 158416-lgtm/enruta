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

/**
 * Perfil y estado del conductor. El id coincide con el usuario de auth: auth posee credenciales,
 * dispatch posee perfil y disponibilidad (sin duplicar el dato de identidad).
 */
@Entity
@Table(name = "drivers")
public class Driver {

    @Id private UUID id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "vehicle_type", nullable = false, length = 16)
    private String vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DriverStatus status;

    @Column(name = "current_lat")
    private Double currentLat;

    @Column(name = "current_lng")
    private Double currentLng;

    @Column(nullable = false)
    private double rating;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Driver() {}

    public Driver(
            UUID id, String fullName, String vehicleType, double rating, double lat, double lng) {
        this.id = id;
        this.fullName = fullName;
        this.vehicleType = vehicleType;
        this.rating = rating;
        this.status = DriverStatus.AVAILABLE;
        this.currentLat = lat;
        this.currentLng = lng;
        this.updatedAt = Instant.now();
    }

    public void moveTo(double lat, double lng) {
        this.currentLat = lat;
        this.currentLng = lng;
        this.updatedAt = Instant.now();
    }

    public void markAvailable() {
        this.status = DriverStatus.AVAILABLE;
        this.updatedAt = Instant.now();
    }

    public void markBusy() {
        this.status = DriverStatus.BUSY;
        this.updatedAt = Instant.now();
    }

    public void markOffline() {
        this.status = DriverStatus.OFFLINE;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public Double getCurrentLat() {
        return currentLat;
    }

    public Double getCurrentLng() {
        return currentLng;
    }

    public double getRating() {
        return rating;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
