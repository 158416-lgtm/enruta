package com.enruta.notification.messaging;

import com.enruta.common.messaging.ProcessedEventStore;
import com.enruta.contracts.Topics;
import com.enruta.notification.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume comandos de notificacion publicados por shipment (via outbox).
 *
 * <p>El deduplicado por {@code eventId} evita correos duplicados si Kafka reentrega el mensaje.
 */
@Component
public class NotificationCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationCommandConsumer.class);

    private final NotificationService notifications;
    private final ProcessedEventStore processedEvents;
    private final ObjectMapper objectMapper;

    public NotificationCommandConsumer(
            NotificationService notifications,
            ProcessedEventStore processedEvents,
            ObjectMapper objectMapper) {
        this.notifications = notifications;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.NOTIFICATION_COMMANDS, groupId = "notification-commands")
    public void onCommand(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            UUID eventId = UUID.fromString(envelope.get("eventId").asText());
            if (!processedEvents.markIfNew(eventId, "notification")) {
                log.debug("comando ya procesado eventId={}", eventId);
                return;
            }
            JsonNode payload = envelope.get("payload");
            UUID shipmentId =
                    payload.hasNonNull("shipmentId")
                            ? UUID.fromString(payload.get("shipmentId").asText())
                            : null;

            notifications.createAndAttempt(
                    shipmentId,
                    payload.path("channel").asText("EMAIL"),
                    payload.path("recipient").asText(),
                    payload.path("template").asText("GENERIC"),
                    payload.path("variables").toString());
        } catch (Exception ex) {
            log.error("no se pudo procesar comando de notificacion", ex);
            throw new IllegalStateException("comando de notificacion invalido", ex);
        }
    }
}
