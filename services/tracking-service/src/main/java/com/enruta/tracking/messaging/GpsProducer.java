package com.enruta.tracking.messaging;

import com.enruta.contracts.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publica los pings crudos en {@code tracking.gps.raw} con el conductor como particion. */
@Component
public class GpsProducer {

    private static final Logger log = LoggerFactory.getLogger(GpsProducer.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public GpsProducer(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    public void publish(
            UUID driverId, List<com.enruta.tracking.web.LocationBatchRequest.Point> points) {
        for (var point : points) {
            try {
                Map<String, Object> payload =
                        Map.of(
                                "driverId", driverId.toString(),
                                "lat", point.lat(),
                                "lng", point.lng(),
                                "recordedAt", Instant.now().toString());
                kafka.send(
                        Topics.TRACKING_GPS_RAW,
                        driverId.toString(),
                        objectMapper.writeValueAsString(payload));
            } catch (Exception ex) {
                log.warn("no se pudo publicar ping driver={}: {}", driverId, ex.getMessage());
            }
        }
    }
}
