package com.enruta.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.contracts.EventEnvelope;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class OutboxAppenderTest {

    @Test
    void appendsTheSerializedEnvelopeToTheOutbox() throws Exception {
        OutboxRepository repository = mock(OutboxRepository.class);
        OutboxAppender appender =
                new OutboxAppender(repository, new ObjectMapper().findAndRegisterModules());
        EventEnvelope<String> envelope =
                EventEnvelope.of("shipment.created", 1, UUID.randomUUID(), "trace-1", "payload");

        UUID returned = appender.append(envelope, "shipment.events");

        assertThat(returned).isEqualTo(envelope.eventId());
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertThat(event.getId()).isEqualTo(envelope.eventId());
        assertThat(event.getAggregateId()).isEqualTo(envelope.aggregateId());
        assertThat(event.getEventType()).isEqualTo("shipment.created");
        assertThat(event.getTopic()).isEqualTo("shipment.events");
        assertThat(event.getPayload()).contains("\"eventType\":\"shipment.created\"");
        assertThat(event.getCreatedAt()).isNotNull();
    }

    @Test
    void serializationFailuresBecomeIllegalState() throws Exception {
        OutboxRepository repository = mock(OutboxRepository.class);
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any()))
                .thenThrow(new JsonMappingException("no serializable"));
        OutboxAppender appender = new OutboxAppender(repository, mapper);
        EventEnvelope<String> envelope =
                EventEnvelope.of("shipment.created", 1, UUID.randomUUID(), "trace-1", "payload");

        assertThatThrownBy(() -> appender.append(envelope, "shipment.events"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shipment.created");
        verify(repository, never()).save(any());
    }

    @Test
    void appendRequiresAnActiveTransaction() throws Exception {
        Transactional transactional =
                OutboxAppender.class
                        .getMethod("append", EventEnvelope.class, String.class)
                        .getAnnotation(Transactional.class);

        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
