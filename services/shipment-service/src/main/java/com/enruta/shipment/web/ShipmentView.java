package com.enruta.shipment.web;

import com.enruta.shipment.domain.Shipment;
import com.enruta.shipment.domain.ShipmentStatus;
import com.enruta.shipment.domain.ShipmentStatusHistory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShipmentView(
        UUID id,
        UUID customerId,
        UUID driverId,
        ShipmentStatus status,
        Coordinate pickup,
        Coordinate dropoff,
        String pickupPin,
        int dispatchRounds,
        Long etaSeconds,
        Instant createdAt,
        Instant updatedAt,
        List<HistoryItem> history) {

    public record Coordinate(double lat, double lng, String address) {}

    public record HistoryItem(
            ShipmentStatus from,
            ShipmentStatus to,
            Instant occurredAt,
            UUID actorId,
            String reason) {}

    public static ShipmentView from(Shipment shipment, List<ShipmentStatusHistory> history) {
        return new ShipmentView(
                shipment.getId(),
                shipment.getCustomerId(),
                shipment.getDriverId(),
                shipment.getStatus(),
                new Coordinate(
                        shipment.getPickupLat(),
                        shipment.getPickupLng(),
                        shipment.getPickupAddress()),
                new Coordinate(
                        shipment.getDropoffLat(),
                        shipment.getDropoffLng(),
                        shipment.getDropoffAddress()),
                shipment.getPickupPin(),
                shipment.getDispatchRounds(),
                null,
                shipment.getCreatedAt(),
                shipment.getUpdatedAt(),
                history.stream()
                        .map(
                                h ->
                                        new HistoryItem(
                                                h.getFromStatus(),
                                                h.getToStatus(),
                                                h.getOccurredAt(),
                                                h.getActorId(),
                                                h.getReason()))
                        .toList());
    }
}
