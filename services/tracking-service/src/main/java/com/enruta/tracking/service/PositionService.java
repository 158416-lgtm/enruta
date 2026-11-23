package com.enruta.tracking.service;

import com.enruta.common.geo.GeoMath;
import com.enruta.contracts.EventEnvelope;
import com.enruta.contracts.EventTypes;
import com.enruta.contracts.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Procesa cada posicion: actualiza Redis, evalua geocercas y empuja por WebSocket.
 *
 * <p>Tracking no tiene base de datos transaccional: su estado es efimero y reconstruible. Por eso
 * no usa el patron outbox (ADR-0009); si se pierde un ping, el siguiente lo corrige.
 */
@Service
public class PositionService {

    private static final Logger log = LoggerFactory.getLogger(PositionService.class);
    private static final double PICKUP_RADIUS_METERS = 100;
    private static final double DROPOFF_RADIUS_METERS = 150;
    private static final double TRIP_START_METERS = 150;
    private static final String AVAILABLE_KEY = "drivers:available";

    private final StringRedisTemplate redis;
    private final GeofenceService geofences;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final TrackingBroadcaster broadcaster;

    public PositionService(
            StringRedisTemplate redis,
            GeofenceService geofences,
            KafkaTemplate<String, String> kafka,
            ObjectMapper objectMapper,
            TrackingBroadcaster broadcaster) {
        this.redis = redis;
        this.geofences = geofences;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.broadcaster = broadcaster;
    }

    public record Position(UUID driverId, double lat, double lng, Instant recordedAt) {}

    public void handle(Position position) {
        String driverKey = "driver:" + position.driverId() + ":last";
        redis.opsForHash()
                .putAll(
                        driverKey,
                        Map.of(
                                "lat", String.valueOf(position.lat()),
                                "lng", String.valueOf(position.lng()),
                                "at", position.recordedAt().toString()));
        redis.expire(driverKey, Duration.ofMinutes(10));

        // Refresca el indice de despacho solo si dispatch ya marco al conductor como disponible.
        Double availableRank =
                redis.opsForZSet().score(AVAILABLE_KEY, position.driverId().toString());
        if (availableRank != null) {
            redis.opsForGeo()
                    .add(
                            AVAILABLE_KEY,
                            new org.springframework.data.geo.Point(position.lng(), position.lat()),
                            position.driverId().toString());
        }

        String shipmentId = redis.opsForValue().get("driver:" + position.driverId() + ":shipment");
        if (shipmentId == null) {
            return;
        }
        Map<Object, Object> shipment = redis.opsForHash().entries("shipment:" + shipmentId);
        if (shipment.isEmpty()) {
            return;
        }

        broadcaster.position(shipmentId, position);

        double pickupLat = Double.parseDouble(shipment.get("pickupLat").toString());
        double pickupLng = Double.parseDouble(shipment.get("pickupLng").toString());
        double dropoffLat = Double.parseDouble(shipment.get("dropoffLat").toString());
        double dropoffLng = Double.parseDouble(shipment.get("dropoffLng").toString());

        double toPickup =
                GeoMath.distanceMeters(position.lat(), position.lng(), pickupLat, pickupLng);
        double toDropoff =
                GeoMath.distanceMeters(position.lat(), position.lng(), dropoffLat, dropoffLng);

        boolean pickupConfirmed =
                geofences.registerHit(shipmentId, "PICKUP", toPickup <= PICKUP_RADIUS_METERS);
        if (pickupConfirmed) {
            publishGeofence(shipmentId, position.driverId(), "PICKUP", toPickup);
        }

        boolean pickupDone = geofences.alreadyConfirmed(shipmentId, "PICKUP");
        String tripKey = "geo:" + shipmentId + ":TRIP:done";
        if (pickupDone
                && toPickup > TRIP_START_METERS
                && !Boolean.TRUE.equals(redis.hasKey(tripKey))) {
            redis.opsForValue().set(tripKey, "1", Duration.ofHours(6));
            publishEvent(
                    EventTypes.TRACKING_TRIP_STARTED,
                    shipmentId,
                    Map.of("shipmentId", shipmentId, "driverId", position.driverId().toString()));
        }

        boolean dropoffConfirmed =
                geofences.registerHit(shipmentId, "DROPOFF", toDropoff <= DROPOFF_RADIUS_METERS);
        if (dropoffConfirmed) {
            publishGeofence(shipmentId, position.driverId(), "DROPOFF", toDropoff);
        }
    }

    private void publishGeofence(String shipmentId, UUID driverId, String fence, double meters) {
        publishEvent(
                EventTypes.TRACKING_GEOFENCE_ENTERED,
                shipmentId,
                Map.of(
                        "shipmentId",
                        shipmentId,
                        "driverId",
                        driverId.toString(),
                        "fence",
                        fence,
                        "distanceMeters",
                        Math.round(meters)));
    }

    private void publishEvent(String type, String shipmentId, Map<String, Object> payload) {
        try {
            EventEnvelope<Map<String, Object>> envelope =
                    EventEnvelope.of(type, 1, UUID.fromString(shipmentId), null, payload);
            kafka.send(
                    Topics.TRACKING_EVENTS, shipmentId, objectMapper.writeValueAsString(envelope));
            log.info("evento tracking publicado type={} shipment={}", type, shipmentId);
        } catch (Exception ex) {
            log.warn(
                    "no se pudo publicar evento tracking type={} shipment={}",
                    type,
                    shipmentId,
                    ex);
        }
    }
}
