package com.enruta.dispatch.messaging;

import java.time.Instant;
import java.util.UUID;

/** Cuerpos de los eventos que publica dispatch. */
public final class DispatchEvents {

    public record OfferCreated(
            UUID offerId,
            UUID shipmentId,
            UUID driverId,
            Instant expiresAt,
            short round,
            int distanceMeters,
            int etaSeconds) {}

    public record Assigned(
            UUID assignmentId,
            UUID shipmentId,
            UUID driverId,
            UUID previousDriverId,
            Instant acceptedAt) {}

    public record AssignmentCancelled(UUID assignmentId, UUID shipmentId, UUID driverId) {}

    public record DispatchFailed(UUID shipmentId, String reason) {}

    private DispatchEvents() {}
}
