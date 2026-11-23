package com.enruta.shipment.messaging;

import com.enruta.common.messaging.ProcessedEventStore;
import com.enruta.contracts.EventTypes;
import com.enruta.contracts.Topics;
import com.enruta.shipment.service.ShipmentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Traduce hechos fisicos (geocercas) en transiciones del envio.
 *
 * <p>Tracking detecta "el conductor llego al origen" o "llego al destino"; shipment decide si eso
 * cambia su estado. Ninguno escribe el dato del otro.
 */
@Component
public class TrackingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TrackingEventConsumer.class);

    private final ShipmentService shipmentService;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper objectMapper;

    public TrackingEventConsumer(
            ShipmentService shipmentService,
            ProcessedEventStore processedEvents,
            ObjectMapper objectMapper) {
        this.shipmentService = shipmentService;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.TRACKING_EVENTS, groupId = "shipment-tracking-events")
    public void onTrackingEvent(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            UUID eventId = UUID.fromString(envelope.get("eventId").asText());
            if (!processedEvents.markIfNew(eventId, "shipment-tracking")) {
                log.debug("evento ya procesado eventId={}", eventId);
                return;
            }
            String type = envelope.get("eventType").asText();
            JsonNode payload = envelope.get("payload");
            UUID shipmentId = UUID.fromString(payload.get("shipmentId").asText());

            switch (type) {
                case EventTypes.TRACKING_GEOFENCE_ENTERED -> {
                    String fence = payload.get("fence").asText();
                    if ("PICKUP".equals(fence)) {
                        shipmentService.onPickupConfirmed(shipmentId);
                    } else if ("DROPOFF".equals(fence)) {
                        shipmentService.onDelivered(shipmentId);
                    }
                }
                case EventTypes.TRACKING_TRIP_STARTED -> shipmentService.onTripStarted(shipmentId);
                default -> log.debug("evento de tracking ignorado type={}", type);
            }
        } catch (Exception ex) {
            log.error("no se pudo procesar evento de tracking", ex);
            throw new IllegalStateException("evento de tracking invalido", ex);
        }
    }
}
