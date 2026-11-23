package com.enruta.common.outbox;

import com.enruta.contracts.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Punto unico de escritura del outbox. Cualquier servicio lo usa dentro de su transaccion. */
public class OutboxAppender {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxAppender(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(EventEnvelope<?> envelope, String topic) {
        try {
            String payload = objectMapper.writeValueAsString(envelope);
            repository.save(
                    new OutboxEvent(
                            envelope.eventId(),
                            envelope.aggregateId(),
                            envelope.eventType(),
                            topic,
                            payload,
                            Instant.now()));
            return envelope.eventId();
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(
                    "No se pudo serializar el evento " + envelope.eventType(), ex);
        }
    }
}
