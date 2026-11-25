package com.enruta.notification.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.common.messaging.ProcessedEventStore;
import com.enruta.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationCommandConsumerTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final ProcessedEventStore processedEvents = mock(ProcessedEventStore.class);

    private NotificationCommandConsumer consumer;
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer =
                new NotificationCommandConsumer(notifications, processedEvents, new ObjectMapper());
    }

    private String command(String payload) {
        return "{\"eventId\":\"" + eventId + "\",\"payload\":" + payload + "}";
    }

    @Test
    void createsAndAttemptsTheNotification() {
        UUID shipmentId = UUID.randomUUID();
        when(processedEvents.markIfNew(eventId, "notification")).thenReturn(true);

        consumer.onCommand(
                command(
                        "{\"shipmentId\":\""
                                + shipmentId
                                + "\",\"channel\":\"EMAIL\",\"recipient\":\"cliente@enruta.test\","
                                + "\"template\":\"SHIPMENT_CREATED\",\"variables\":{\"a\":1}}"));

        verify(notifications)
                .createAndAttempt(
                        eq(shipmentId),
                        eq("EMAIL"),
                        eq("cliente@enruta.test"),
                        eq("SHIPMENT_CREATED"),
                        anyString());
    }

    @Test
    void appliesDefaultsWhenFieldsAreMissing() {
        when(processedEvents.markIfNew(eventId, "notification")).thenReturn(true);

        consumer.onCommand(command("{\"recipient\":\"cliente@enruta.test\",\"variables\":{}}"));

        verify(notifications)
                .createAndAttempt(
                        isNull(),
                        eq("EMAIL"),
                        eq("cliente@enruta.test"),
                        eq("GENERIC"),
                        anyString());
    }

    @Test
    void duplicatedCommandIsIgnored() {
        when(processedEvents.markIfNew(eventId, "notification")).thenReturn(false);

        consumer.onCommand(command("{\"recipient\":\"cliente@enruta.test\"}"));

        verify(notifications, never()).createAndAttempt(any(), any(), any(), any(), any());
    }

    @Test
    void invalidMessageIsRejected() {
        assertThatThrownBy(() -> consumer.onCommand("{roto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notificacion");
    }
}
