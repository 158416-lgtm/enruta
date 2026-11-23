package com.enruta.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock private OutboxRepository repository;

    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, kafkaTemplate);
    }

    @Test
    void publishesWithAggregateIdAsKeyAndMarksPublished() {
        OutboxEvent event = event("shipment.events");
        when(repository.lockPending(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

        relay.publishPending();

        verify(kafkaTemplate).send("shipment.events", event.getAggregateId().toString(), "{}");
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void failedSendKeepsRowPendingAndCountsAttempt() {
        OutboxEvent event = event("shipment.events");
        when(repository.lockPending(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(
                        CompletableFuture.failedFuture(new IllegalStateException("broker caido")));

        relay.publishPending();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo((short) 1);
    }

    @Test
    void doesNothingWhenOutboxIsEmpty() {
        when(repository.lockPending(100)).thenReturn(List.of());

        relay.publishPending();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    private OutboxEvent event(String topic) {
        return new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "shipment.created",
                topic,
                "{}",
                Instant.now());
    }
}
