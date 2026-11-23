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
 * Aplica al envio lo que decide el despacho.
 *
 * <p>Es la mitad "receptora" de la saga coreografiada: dispatch no toca la tabla de envios, solo
 * publica hechos; shipment es el unico dueno de su estado.
 */
@Component
public class DispatchEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DispatchEventConsumer.class);

    private final ShipmentService shipmentService;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper objectMapper;

    public DispatchEventConsumer(
            ShipmentService shipmentService,
            ProcessedEventStore processedEvents,
            ObjectMapper objectMapper) {
        this.shipmentService = shipmentService;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.DISPATCH_EVENTS, groupId = "shipment-dispatch-events")
    public void onDispatchEvent(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            UUID eventId = UUID.fromString(envelope.get("eventId").asText());
            if (!processedEvents.markIfNew(eventId, "shipment")) {
                log.debug("evento ya procesado eventId={}", eventId);
                return;
            }
            String type = envelope.get("eventType").asText();
            JsonNode payload = envelope.get("payload");
            UUID shipmentId = UUID.fromString(payload.get("shipmentId").asText());

            switch (type) {
                case EventTypes.DISPATCH_ASSIGNED -> {
                    UUID driverId = UUID.fromString(payload.get("driverId").asText());
                    shipmentService.onDispatchAssigned(shipmentId, driverId);
                }
                case EventTypes.DISPATCH_ASSIGNMENT_CANCELLED ->
                        shipmentService.onDriverCancelled(shipmentId);
                case EventTypes.DISPATCH_FAILED -> shipmentService.onNoDriver(shipmentId);
                case EventTypes.DISPATCH_OFFER_CREATED ->
                        log.debug("oferta creada shipment={}", shipmentId);
                default -> log.debug("evento de dispatch ignorado type={}", type);
            }
        } catch (Exception ex) {
            log.error("no se pudo procesar evento de dispatch", ex);
            throw new IllegalStateException("evento de dispatch invalido", ex);
        }
    }
}
