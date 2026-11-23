package com.enruta.dispatch.messaging;

import com.enruta.common.messaging.ProcessedEventStore;
import com.enruta.contracts.EventTypes;
import com.enruta.contracts.Topics;
import com.enruta.dispatch.service.DispatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Arranca el despacho cuando nace un envio.
 *
 * <p>Cada evento se deduplica por {@code eventId}: un rebalance o un reproceso no debe generar dos
 * rondas de ofertas para el mismo envio.
 */
@Component
public class ShipmentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShipmentEventConsumer.class);

    private final DispatchService dispatchService;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper objectMapper;

    public ShipmentEventConsumer(
            DispatchService dispatchService,
            ProcessedEventStore processedEvents,
            ObjectMapper objectMapper) {
        this.dispatchService = dispatchService;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.SHIPMENT_EVENTS, groupId = "dispatch-shipment-events")
    public void onShipmentEvent(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            UUID eventId = UUID.fromString(envelope.get("eventId").asText());
            if (!processedEvents.markIfNew(eventId, "dispatch")) {
                log.debug("evento ya procesado eventId={}", eventId);
                return;
            }
            String type = envelope.get("eventType").asText();
            JsonNode payload = envelope.get("payload");
            UUID shipmentId = UUID.fromString(payload.get("shipmentId").asText());

            switch (type) {
                case EventTypes.SHIPMENT_CREATED ->
                        dispatchService.onShipmentCreated(
                                shipmentId,
                                payload.get("pickupLat").asDouble(),
                                payload.get("pickupLng").asDouble());
                case EventTypes.SHIPMENT_UNASSIGNED -> dispatchService.restart(shipmentId);
                case EventTypes.SHIPMENT_CANCELLED -> dispatchService.cancelShipment(shipmentId);
                default -> log.debug("evento ignorado type={}", type);
            }
        } catch (Exception ex) {
            log.error("no se pudo procesar evento de shipment", ex);
            throw new IllegalStateException("evento de shipment invalido", ex);
        }
    }
}
