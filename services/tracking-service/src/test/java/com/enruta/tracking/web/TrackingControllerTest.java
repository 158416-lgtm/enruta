package com.enruta.tracking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.enruta.common.error.BusinessException;
import com.enruta.tracking.service.EtaClient;
import com.enruta.tracking.service.TrackingBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class TrackingControllerTest {

    @Mock private StringRedisTemplate redis;
    @Mock private HashOperations<String, Object, Object> hashOps;
    @Mock private ListOperations<String, String> listOps;
    @Mock private TrackingBroadcaster broadcaster;
    @Mock private EtaClient etaClient;

    private TrackingController controller;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID shipmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new TrackingController(redis, broadcaster, etaClient, objectMapper);
        // No todos los tests consultan el hash; el stub es comun a la mayoria.
        org.mockito.Mockito.lenient().when(redis.opsForHash()).thenReturn(hashOps);
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

    @Test
    void stateOmitsEtaWhenRoutingDoesNotAnswer() {
        when(hashOps.entries("shipment:" + shipmentId))
                .thenReturn(
                        Map.of(
                                "dropoffLat", "4.3374",
                                "dropoffLng", "-74.36",
                                "driverId", "d1"));
        when(hashOps.entries("driver:d1:last")).thenReturn(Map.of("lat", "4.34", "lng", "-74.36"));
        when(etaClient.etaSeconds(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(null);

        Map<String, Object> response = controller.state(shipmentId, "Bearer x");

        assertThat(response).containsKey("distanceToDropoffMeters");
        assertThat(response).doesNotContainKey("etaSeconds");
    }

    @Test
    void trailParsesStoredPointsAndSkipsCorruptOnes() {
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.range("shipment:" + shipmentId + ":trail", 0, 199))
                .thenReturn(
                        List.of(
                                "{\"lat\":4.34,\"lng\":-74.36,\"at\":\"2026-11-25T10:00:00Z\"}",
                                "{roto"));

        Map<String, Object> response = controller.trail(shipmentId);

        assertThat(response).containsEntry("count", 1);
        assertThat((List<?>) response.get("points")).hasSize(1);
    }

    @Test
    void trailWithoutPointsReturnsEmptyList() {
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.range("shipment:" + shipmentId + ":trail", 0, 199)).thenReturn(null);

        Map<String, Object> response = controller.trail(shipmentId);

        assertThat(response).containsEntry("count", 0);
    }

    @Test
    void lastPositionReturnsCoordinates() {
        UUID driverId = UUID.randomUUID();
        when(hashOps.entries("driver:" + driverId + ":last"))
                .thenReturn(Map.of("lat", "4.34", "lng", "-74.36", "at", "2026-11-25T10:00:00Z"));

        Map<String, Object> response = controller.lastPosition(driverId);

        assertThat(response).containsEntry("lat", 4.34).containsEntry("lng", -74.36);
    }

    @Test
    void lastPositionFailsWhenDriverHasNeverReported() {
        UUID driverId = UUID.randomUUID();
        when(hashOps.entries("driver:" + driverId + ":last")).thenReturn(Map.of());

        assertThatThrownBy(() -> controller.lastPosition(driverId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void statsCountsTrailsAndDrivers() {
        when(redis.keys("shipment:*:trail"))
                .thenReturn(Set.of("shipment:a:trail", "shipment:b:trail"));
        when(redis.keys("driver:*:last")).thenReturn(Set.of("driver:a:last"));

        Map<String, Object> response = controller.stats();

        assertThat(response)
                .containsEntry("shipmentsWithTrail", 2)
                .containsEntry("driversWithPosition", 1);
    }
}
