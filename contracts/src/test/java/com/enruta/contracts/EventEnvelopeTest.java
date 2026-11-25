package com.enruta.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    void ofBuildsACompleteEnvelope() {
        UUID aggregate = UUID.randomUUID();

        EventEnvelope<String> envelope =
                EventEnvelope.of("shipment.created", 1, aggregate, "trace-1", "ok");

        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.eventType()).isEqualTo("shipment.created");
        assertThat(envelope.eventVersion()).isEqualTo(1);
        assertThat(envelope.aggregateId()).isEqualTo(aggregate);
        assertThat(envelope.traceId()).isEqualTo("trace-1");
        assertThat(envelope.payload()).isEqualTo("ok");
        assertThat(envelope.occurredAt()).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    void everyEnvelopeGetsItsOwnId() {
        UUID aggregate = UUID.randomUUID();

        EventEnvelope<String> first = EventEnvelope.of("a", 1, aggregate, null, "x");
        EventEnvelope<String> second = EventEnvelope.of("a", 1, aggregate, null, "x");

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }

    @Test
    void rejectsMissingRequiredFields() {
        UUID aggregate = UUID.randomUUID();

        assertThatThrownBy(() -> EventEnvelope.of(null, 1, aggregate, null, "x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EventEnvelope.of("a", 1, null, null, "x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> EventEnvelope.of("a", 1, aggregate, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void topicHelpersAreConsistent() {
        assertThat(Topics.dlqOf(Topics.SHIPMENT_EVENTS)).isEqualTo("shipment.events.dlq");
        assertThat(Topics.dlqOf(Topics.NOTIFICATION_COMMANDS))
                .isEqualTo("notification.commands.dlq");
    }
}
