package com.enruta.tracking.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TrackingReadModelTest {

    @Mock private StringRedisTemplate redis;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private ValueOperations<String, String> valueOps;

    private TrackingReadModel readModel;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final String shipmentId = UUID.randomUUID().toString();
    private final String driverId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        readModel = new TrackingReadModel(redis, mapper);
    }

    private String envelope(String type, String payload) {
        return """
        {"eventId":"%s","eventType":"%s","payload":%s}
        """
                .formatted(UUID.randomUUID(), type, payload);
    }

    @Test
    void aCreatedShipmentIsStoredWithItsCoordinates() {
        when(redis.opsForHash()).thenReturn(hashOps);

        readModel.onEvent(
                envelope(
                        "shipment.created",
                        "{\"shipmentId\":\"%s\",\"pickupLat\":4.34,\"pickupLng\":-74.36,"
                                        .formatted(shipmentId)
                                + "\"dropoffLat\":4.33,\"dropoffLng\":-74.35}"));

        verify(hashOps).putAll(eq("shipment:" + shipmentId), anyMap());
        verify(redis).expire(eq("shipment:" + shipmentId), any(Duration.class));
    }

    @Test
    void anAssignmentBindsDriverAndShipment() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForHash()).thenReturn(hashOps);

        readModel.onEvent(
                envelope(
                        "dispatch.assigned",
                        "{\"shipmentId\":\"%s\",\"driverId\":\"%s\"}"
                                .formatted(shipmentId, driverId)));

        verify(valueOps)
                .set(eq("driver:" + driverId + ":shipment"), eq(shipmentId), any(Duration.class));
        verify(hashOps).put("shipment:" + shipmentId, "driverId", driverId);
    }

    @Test
    void aCancelledShipmentReleasesTheDriver() {
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.get("shipment:" + shipmentId, "driverId")).thenReturn(driverId);

        readModel.onEvent(
                envelope(
                        "shipment.status-changed",
                        "{\"shipmentId\":\"%s\",\"status\":\"CANCELLED\"}".formatted(shipmentId)));

        verify(redis).delete("driver:" + driverId + ":shipment");
    }

    @Test
    void aBrokenMessageIsIgnored() {
        readModel.onEvent("esto no es json");
        readModel.onEvent(envelope("shipment.created", "{}"));
        // no lanza
    }

    @Test
    void unrelatedEventsAreIgnored() {
        readModel.onEvent(
                envelope(
                        "dispatch.offer-created", "{\"shipmentId\":\"%s\"}".formatted(shipmentId)));
        verify(redis, org.mockito.Mockito.never()).opsForHash();
    }

    @Test
    void anEventWithoutPayloadIsIgnored() {
        readModel.onEvent(
                """
                {"eventId":"%s","eventType":"shipment.created"}
                """
                        .formatted(UUID.randomUUID()));

        verify(redis, org.mockito.Mockito.never()).opsForHash();
    }

    @Test
    void aShipmentAssignedEventBindsDriverAndShipment() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForHash()).thenReturn(hashOps);

        readModel.onEvent(
                envelope(
                        "shipment.assigned",
                        "{\"shipmentId\":\"%s\",\"driverId\":\"%s\"}"
                                .formatted(shipmentId, driverId)));

        verify(valueOps)
                .set(eq("driver:" + driverId + ":shipment"), eq(shipmentId), any(Duration.class));
        verify(hashOps).put("shipment:" + shipmentId, "driverId", driverId);
        verify(redis).expire(eq("shipment:" + shipmentId), any(Duration.class));
    }

    @Test
    void anAssignmentWithoutDriverOnlyTouchesNothing() {
        readModel.onEvent(
                envelope(
                        "dispatch.assigned",
                        "{\"shipmentId\":\"%s\",\"driverId\":null}".formatted(shipmentId)));

        verify(redis, org.mockito.Mockito.never()).opsForValue();
        verify(redis, org.mockito.Mockito.never()).opsForHash();
    }

    @Test
    void aDeliveredStatusReleasesTheDriver() {
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.get("shipment:" + shipmentId, "driverId")).thenReturn(driverId);

        readModel.onEvent(
                envelope(
                        "shipment.status-changed",
                        "{\"shipmentId\":\"%s\",\"status\":\"DELIVERED\"}".formatted(shipmentId)));

        verify(redis).delete("driver:" + driverId + ":shipment");
    }

    @Test
    void aNonTerminalStatusKeepsTheDriver() {
        readModel.onEvent(
                envelope(
                        "shipment.status-changed",
                        "{\"shipmentId\":\"%s\",\"status\":\"IN_TRANSIT\"}".formatted(shipmentId)));

        verify(redis, org.mockito.Mockito.never()).opsForHash();
        verify(redis, org.mockito.Mockito.never()).delete(any(String.class));
    }

    @Test
    void aDeliveredEventReleasesTheDriver() {
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.get("shipment:" + shipmentId, "driverId")).thenReturn(driverId);

        readModel.onEvent(
                envelope("shipment.delivered", "{\"shipmentId\":\"%s\"}".formatted(shipmentId)));

        verify(redis).delete("driver:" + driverId + ":shipment");
    }

    @Test
    void aCancelledEventWithoutAssignedDriverDeletesNothing() {
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.get("shipment:" + shipmentId, "driverId")).thenReturn(null);

        readModel.onEvent(
                envelope("shipment.cancelled", "{\"shipmentId\":\"%s\"}".formatted(shipmentId)));

        verify(redis, org.mockito.Mockito.never()).delete(any(String.class));
    }
}
