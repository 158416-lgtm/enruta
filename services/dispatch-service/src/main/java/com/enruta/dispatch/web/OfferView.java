package com.enruta.dispatch.web;

import com.enruta.dispatch.domain.OfferStatus;
import java.time.Instant;
import java.util.UUID;

public record OfferView(
        UUID id,
        UUID shipmentId,
        UUID driverId,
        OfferStatus status,
        Instant expiresAt,
        short round,
        Integer distanceMeters,
        int etaSeconds) {}
