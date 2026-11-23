package com.enruta.contracts;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Envoltorio comun a todos los eventos del sistema.
 *
 * <p>El campo {@code aggregateId} es el partition key de Kafka: garantiza orden por agregado (por
 * ejemplo, todas las posiciones de un mismo conductor caen en la misma particion).
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID aggregateId,
        String traceId,
        T payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(payload, "payload");
    }

    public static <T> EventEnvelope<T> of(
            String eventType, int version, UUID aggregateId, String traceId, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                version,
                Instant.now(),
                aggregateId,
                traceId,
                payload);
    }
}
