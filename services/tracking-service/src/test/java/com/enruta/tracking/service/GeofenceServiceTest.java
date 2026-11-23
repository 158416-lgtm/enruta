package com.enruta.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GeofenceServiceTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @Autowired private GeofenceService geofences;
    @Autowired private StringRedisTemplate redis;

    private String shipmentId;

    @BeforeEach
    void setUp() {
        shipmentId = UUID.randomUUID().toString();
    }

    @Test
    void requiresThreeConsecutiveHits() {
        assertThat(geofences.registerHit(shipmentId, "PICKUP", true)).isFalse();
        assertThat(geofences.registerHit(shipmentId, "PICKUP", true)).isFalse();
        assertThat(geofences.registerHit(shipmentId, "PICKUP", true)).isTrue();
        // Una vez confirmada no se vuelve a disparar aunque siga dentro.
        assertThat(geofences.registerHit(shipmentId, "PICKUP", true)).isFalse();
        assertThat(geofences.alreadyConfirmed(shipmentId, "PICKUP")).isTrue();
    }

    @Test
    void leavingTheFenceResetsTheCounter() {
        geofences.registerHit(shipmentId, "DROPOFF", true);
        geofences.registerHit(shipmentId, "DROPOFF", true);
        assertThat(geofences.registerHit(shipmentId, "DROPOFF", false)).isFalse();
        assertThat(geofences.registerHit(shipmentId, "DROPOFF", true)).isFalse();
        assertThat(geofences.registerHit(shipmentId, "DROPOFF", true)).isFalse();
        assertThat(geofences.registerHit(shipmentId, "DROPOFF", true)).isTrue();
    }

    @Test
    void fencesAreIndependentPerShipment() {
        String other = UUID.randomUUID().toString();
        geofences.registerHit(shipmentId, "PICKUP", true);
        geofences.registerHit(shipmentId, "PICKUP", true);
        geofences.registerHit(shipmentId, "PICKUP", true);

        assertThat(geofences.alreadyConfirmed(shipmentId, "PICKUP")).isTrue();
        assertThat(geofences.alreadyConfirmed(other, "PICKUP")).isFalse();
        assertThat(geofences.alreadyConfirmed(shipmentId, "DROPOFF")).isFalse();
    }
}
