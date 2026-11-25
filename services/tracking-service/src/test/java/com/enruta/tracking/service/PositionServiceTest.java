package com.enruta.tracking.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private ZSetOperations<String, String> zsetOps;
    @Mock private GeoOperations<String, String> geoOps;
    @Mock private GeofenceService geofences;
    @Mock private KafkaTemplate<String, String> kafka;
    @Mock private TrackingBroadcaster broadcaster;

    private PositionService service;
    private final UUID driverId = UUID.randomUUID();
    private final String shipmentId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        service =
                new PositionService(
                        redis,
                        geofences,
                        kafka,
                        new ObjectMapper().findAndRegisterModules(),
                        broadcaster);
    }

    private void stubRedisBasics() {
        org.mockito.Mockito.lenient().when(redis.opsForHash()).thenReturn(hashOps);
        org.mockito.Mockito.lenient().when(redis.opsForValue()).thenReturn(valueOps);
        org.mockito.Mockito.lenient().when(redis.opsForZSet()).thenReturn(zsetOps);
        org.mockito.Mockito.lenient().when(redis.opsForGeo()).thenReturn(geoOps);
    }

    private Map<Object, Object> shipmentHash() {
        return Map.of(
                "pickupLat", "4.3449",
                "pickupLng", "-74.3674",
                "dropoffLat", "4.3374",
                "dropoffLng", "-74.36",
                "driverId", driverId.toString());
    }

    @Test
    void aPositionWithoutShipmentOnlyUpdatesRedis() {
        stubRedisBasics();
        when(zsetOps.score(anyString(), anyString())).thenReturn(null);
        when(valueOps.get("driver:" + driverId + ":shipment")).thenReturn(null);

        service.handle(new PositionService.Position(driverId, 4.34, -74.36, Instant.now()));

        verify(hashOps).putAll(eq("driver:" + driverId + ":last"), anyMap());
        verify(redis).expire(eq("driver:" + driverId + ":last"), any(Duration.class));
        verify(broadcaster, never()).position(anyString(), any());
        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void anAvailableDriverRefreshesTheGeoIndex() {
        stubRedisBasics();
        when(zsetOps.score(anyString(), anyString())).thenReturn(1.0);
        when(valueOps.get(anyString())).thenReturn(null);

        service.handle(new PositionService.Position(driverId, 4.34, -74.36, Instant.now()));

        verify(geoOps).add(eq("drivers:available"), any(), eq(driverId.toString()));
    }

    @Test
    void aPickupGeofencePublishesAnEventAndBroadcasts() {
        stubRedisBasics();
        when(zsetOps.score(anyString(), anyString())).thenReturn(null);
        when(valueOps.get("driver:" + driverId + ":shipment")).thenReturn(shipmentId);
        when(hashOps.entries("shipment:" + shipmentId)).thenReturn(shipmentHash());
        when(geofences.registerHit(shipmentId, "PICKUP", true)).thenReturn(true);
        when(geofences.alreadyConfirmed(shipmentId, "PICKUP")).thenReturn(true);
        when(geofences.registerHit(shipmentId, "DROPOFF", false)).thenReturn(false);

        // justo encima del origen
        service.handle(new PositionService.Position(driverId, 4.3449, -74.3674, Instant.now()));

        verify(broadcaster).position(eq(shipmentId), any());
        verify(geofences).registerHit(shipmentId, "PICKUP", true);
        verify(kafka).send(eq("tracking.events"), eq(shipmentId), anyString());
    }

    @Test
    void theDropoffIsNotEvaluatedBeforeThePickup() {
        stubRedisBasics();
        when(zsetOps.score(anyString(), anyString())).thenReturn(null);
        when(valueOps.get(anyString())).thenReturn(shipmentId);
        when(hashOps.entries("shipment:" + shipmentId)).thenReturn(shipmentHash());
        when(geofences.registerHit(shipmentId, "PICKUP", true)).thenReturn(false);
        when(geofences.alreadyConfirmed(shipmentId, "PICKUP")).thenReturn(false);

        service.handle(new PositionService.Position(driverId, 4.3449, -74.3674, Instant.now()));

        verify(geofences, never()).registerHit(eq(shipmentId), eq("DROPOFF"), anyBoolean());
    }

    @Test
    void leavingThePickupPublishesTheTripStartOnce() {
        stubRedisBasics();
        when(zsetOps.score(anyString(), anyString())).thenReturn(null);
        when(valueOps.get(anyString())).thenReturn(shipmentId);
        when(hashOps.entries("shipment:" + shipmentId)).thenReturn(shipmentHash());
        when(geofences.registerHit(shipmentId, "PICKUP", false)).thenReturn(false);
        when(geofences.alreadyConfirmed(shipmentId, "PICKUP")).thenReturn(true);
        when(redis.hasKey("geo:" + shipmentId + ":TRIP:done")).thenReturn(false);

        // a ~1,7 km del origen
        service.handle(new PositionService.Position(driverId, 4.36, -74.36, Instant.now()));

        verify(valueOps).set(eq("geo:" + shipmentId + ":TRIP:done"), eq("1"), any(Duration.class));
        verify(kafka).send(eq("tracking.events"), eq(shipmentId), anyString());
    }

    @Test
    void aKafkaFailureDoesNotBreakTheIngestion() {
        stubRedisBasics();
        when(zsetOps.score(anyString(), anyString())).thenReturn(null);
        when(valueOps.get(anyString())).thenReturn(shipmentId);
        when(hashOps.entries("shipment:" + shipmentId)).thenReturn(shipmentHash());
        when(geofences.registerHit(shipmentId, "PICKUP", true)).thenReturn(true);
        when(geofences.alreadyConfirmed(shipmentId, "PICKUP")).thenReturn(false);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("broker caido"));

        service.handle(new PositionService.Position(driverId, 4.3449, -74.3674, Instant.now()));
        // no debe lanzar: el ping siguiente corrige
    }
}
