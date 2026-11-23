package com.enruta.shipment.service;

import com.enruta.shipment.domain.Shipment;
import com.enruta.shipment.domain.ShipmentStatus;
import java.util.UUID;

/** Cuerpo de los eventos de envio. Campos estables: solo se agregan, nunca se renombran. */
public record ShipmentEventPayload(
        UUID shipmentId,
        UUID customerId,
        UUID driverId,
        ShipmentStatus status,
        double pickupLat,
        double pickupLng,
        double dropoffLat,
        double dropoffLng) {

    public static ShipmentEventPayload from(Shipment shipment) {
        return new ShipmentEventPayload(
                shipment.getId(),
                shipment.getCustomerId(),
                shipment.getDriverId(),
                shipment.getStatus(),
                shipment.getPickupLat(),
                shipment.getPickupLng(),
                shipment.getDropoffLat(),
                shipment.getDropoffLng());
    }
}
