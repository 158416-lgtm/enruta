package com.enruta.tracking.messaging;

import com.enruta.contracts.Topics;
import com.enruta.tracking.service.PositionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor del camino de lectura.
 *
 * <p>Va en un grupo distinto al productor para poder escalar la ingesta y el enriquecimiento por
 * separado. Si el consumidor se atrasa, la ingesta sigue aceptando (202) y el lag se ve en
 * metricas.
 */
@Component
public class GpsConsumer {

    private static final Logger log = LoggerFactory.getLogger(GpsConsumer.class);

    private final PositionService positionService;
    private final ObjectMapper objectMapper;

    public GpsConsumer(PositionService positionService, ObjectMapper objectMapper) {
        this.positionService = positionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.TRACKING_GPS_RAW, groupId = "tracking-fanout")
    public void onPing(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            positionService.handle(
                    new PositionService.Position(
                            UUID.fromString(node.get("driverId").asText()),
                            node.get("lat").asDouble(),
                            node.get("lng").asDouble(),
                            Instant.parse(node.get("recordedAt").asText())));
        } catch (Exception ex) {
            log.warn("ping descartado: {}", ex.getMessage());
        }
    }
}
