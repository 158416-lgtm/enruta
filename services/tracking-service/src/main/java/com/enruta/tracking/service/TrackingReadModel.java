package com.enruta.tracking.service;

import com.enruta.contracts.EventTypes;
import com.enruta.contracts.Topics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Read-model minimo en Redis: que envio tiene cada conductor y donde estan sus extremos.
 *
 * <p>Sin esto, tracking tendria que llamar por HTTP a shipment en cada ping (miles por minuto).
 * Redis actua como proyeccion local alimentada por eventos.
 */
@Component
public class TrackingReadModel {

    private static final Logger log = LoggerFactory.getLogger(TrackingReadModel.class);
    private static final Duration TTL = Duration.ofHours(12);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public TrackingReadModel(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {Topics.SHIPMENT_EVENTS, Topics.DISPATCH_EVENTS},
            groupId = "tracking-readmodel")
    public void onEvent(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String type = envelope.get("eventType").asText();
            JsonNode payload = envelope.get("payload");
            if (payload == null || !payload.has("shipmentId")) {
                return;
            }
            String shipmentId = payload.get("shipmentId").asText();

            switch (type) {
                case EventTypes.SHIPMENT_CREATED -> {
                    redis.opsForHash()
                            .putAll(
                                    "shipment:" + shipmentId,
                                    Map.of(
                                            "pickupLat", payload.get("pickupLat").asText(),
                                            "pickupLng", payload.get("pickupLng").asText(),
                                            "dropoffLat", payload.get("dropoffLat").asText(),
                                            "dropoffLng", payload.get("dropoffLng").asText()));
                    redis.expire("shipment:" + shipmentId, TTL);
                }
                case EventTypes.DISPATCH_ASSIGNED, EventTypes.SHIPMENT_ASSIGNED -> {
                    if (payload.hasNonNull("driverId")) {
                        String driverId = payload.get("driverId").asText();
                        redis.opsForValue()
                                .set("driver:" + driverId + ":shipment", shipmentId, TTL);
                        redis.opsForHash().put("shipment:" + shipmentId, "driverId", driverId);
                        redis.expire("shipment:" + shipmentId, TTL);
                        log.info(
                                "read-model: driver={} asignado a shipment={}",
                                driverId,
                                shipmentId);
                    }
                }
                case EventTypes.SHIPMENT_STATUS_CHANGED -> {
                    String status = payload.path("status").asText();
                    if ("DELIVERED".equals(status)
                            || "FAILED".equals(status)
                            || "CANCELLED".equals(status)) {
                        Object driverId =
                                redis.opsForHash().get("shipment:" + shipmentId, "driverId");
                        if (driverId != null) {
                            redis.delete("driver:" + driverId + ":shipment");
                        }
                    }
                }
                case EventTypes.SHIPMENT_DELIVERED,
                        EventTypes.SHIPMENT_FAILED,
                        EventTypes.SHIPMENT_CANCELLED -> {
                    Object driverId = redis.opsForHash().get("shipment:" + shipmentId, "driverId");
                    if (driverId != null) {
                        redis.delete("driver:" + driverId + ":shipment");
                    }
                }
                default -> {
                    // resto de eventos no afecta al read-model
                }
            }
        } catch (Exception ex) {
            log.warn("evento no procesado en read-model: {}", ex.getMessage());
        }
    }
}
