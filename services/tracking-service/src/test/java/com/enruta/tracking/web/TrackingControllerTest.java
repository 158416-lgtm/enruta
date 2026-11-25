package com.enruta.tracking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.enruta.tracking.service.EtaClient;
import com.enruta.tracking.service.TrackingBroadcaster;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class TrackingControllerTest {

    @Mock private StringRedisTemplate redis;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private TrackingBroadcaster broadcaster;
    @Mock private EtaClient etaClient;

    private TrackingController controller;
    private final UUID shipmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new TrackingController(redis, broadcaster, etaClient);
        when(redis.opsForHash()).thenReturn(hashOps);
    }

    @Test
    void stateWithoutDriverOnlyReturnsTheShipment() {
        when(hashOps.entries("shipment:" + shipmentId))
                .thenReturn(Map.of("pickupLat", "4.34", "dropoffLat", "4.33"));

        Map<String, Object> response = controller.state(shipmentId, "Bearer x");

        assertThat(response).containsKey("shipment");
        assertThat(response).doesNotContainKey("lastPosition");
    }

    @Test
    void stateWithDriverIncludesDistanceAndEta() {
        when(hashOps.entries("shipment:" + shipmentId))
                .thenReturn(
                        Map.of(
                                "pickupLat", "4.3449",
                                "pickupLng", "-74.3674",
                                "dropoffLat", "4.3374",
                                "dropoffLng", "-74.36",
                                "driverId", "d1"));
        when(hashOps.entries("driver:d1:last"))
                .thenReturn(Map.of("lat", "4.34", "lng", "-74.36", "at", "2026-11-25T10:00:00Z"));
        when(etaClient.etaSeconds(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(120L);

        Map<String, Object> response = controller.state(shipmentId, "Bearer x");

        assertThat(response).containsKey("lastPosition");
        assertThat(response).containsEntry("etaSeconds", 120L);
        assertThat((Long) response.get("distanceToDropoffMeters")).isPositive();
    }

    @Test
    void stateWithoutLastPositionStillWorks() {
        when(hashOps.entries("shipment:" + shipmentId)).thenReturn(Map.of("driverId", "d1"));
        when(hashOps.entries("driver:d1:last")).thenReturn(Map.of());

        Map<String, Object> response = controller.state(shipmentId, "Bearer x");

        assertThat(response).doesNotContainKey("lastPosition");
    }
}
