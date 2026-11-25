package com.enruta.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @Test
    void exposesTheAuditFields() {
        Instant createdAt = Instant.now().minusSeconds(5);
        UUID id = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

        OutboxEvent event =
                new OutboxEvent(
                        id, aggregateId, "shipment.created", "shipment.events", "{}", createdAt);

        assertThat(event.getId()).isEqualTo(id);
        assertThat(event.getAggregateId()).isEqualTo(aggregateId);
        assertThat(event.getEventType()).isEqualTo("shipment.created");
        assertThat(event.getTopic()).isEqualTo("shipment.events");
        assertThat(event.getPayload()).isEqualTo("{}");
        assertThat(event.getCreatedAt()).isEqualTo(createdAt);
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    void jpaConstructorIsAvailableViaReflection() throws Exception {
        var constructor = OutboxEvent.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(constructor.newInstance()).isNotNull();
    }
}
