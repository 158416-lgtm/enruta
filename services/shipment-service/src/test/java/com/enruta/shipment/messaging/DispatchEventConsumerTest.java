package com.enruta.shipment.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.common.messaging.ProcessedEventStore;
import com.enruta.contracts.EventTypes;
import com.enruta.shipment.service.ShipmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchEventConsumerTest {

    @Mock private ShipmentService shipmentService;
    @Mock private ProcessedEventStore processedEvents;

    private DispatchEventConsumer consumer;
    private final UUID eventId = UUID.randomUUID();
    private final UUID shipmentId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new DispatchEventConsumer(shipmentService, processedEvents, new ObjectMapper());
    }

    private String event(String type, String payload) {
        return "{\"eventId\":\""
                + eventId
                + "\",\"eventType\":\""
                + type
                + "\",\"payload\":"
                + payload
                + "}";
    }

    @Test
    void assignedEventDelegatesToShipmentService() {
        when(processedEvents.markIfNew(eventId, "shipment")).thenReturn(true);

        consumer.onDispatchEvent(
                event(
                        EventTypes.DISPATCH_ASSIGNED,
                        "{\"shipmentId\":\""
                                + shipmentId
                                + "\",\"driverId\":\""
                                + driverId
                                + "\"}"));

        verify(shipmentService).onDispatchAssigned(shipmentId, driverId);
    }

    @Test
    void cancelledEventCompensatesTheSaga() {
        when(processedEvents.markIfNew(eventId, "shipment")).thenReturn(true);

        consumer.onDispatchEvent(
                event(
                        EventTypes.DISPATCH_ASSIGNMENT_CANCELLED,
                        "{\"shipmentId\":\"" + shipmentId + "\"}"));

        verify(shipmentService).onDriverCancelled(shipmentId);
    }

    @Test
    void failedEventMarksShipmentWithoutDriver() {
        when(processedEvents.markIfNew(eventId, "shipment")).thenReturn(true);

        consumer.onDispatchEvent(
                event(EventTypes.DISPATCH_FAILED, "{\"shipmentId\":\"" + shipmentId + "\"}"));

        verify(shipmentService).onNoDriver(shipmentId);
    }

    @Test
    void offerCreatedOnlyLogs() {
        when(processedEvents.markIfNew(eventId, "shipment")).thenReturn(true);

        consumer.onDispatchEvent(
                event(
                        EventTypes.DISPATCH_OFFER_CREATED,
                        "{\"shipmentId\":\"" + shipmentId + "\"}"));

        verify(shipmentService, never()).onDispatchAssigned(any(), any());
    }

    @Test
    void unknownEventIsIgnored() {
        when(processedEvents.markIfNew(eventId, "shipment")).thenReturn(true);

        consumer.onDispatchEvent(event("otro.evento", "{\"shipmentId\":\"" + shipmentId + "\"}"));

        verify(shipmentService, never()).onDispatchAssigned(any(), any());
    }

    @Test
    void duplicatedEventIsSkipped() {
        when(processedEvents.markIfNew(eventId, "shipment")).thenReturn(false);

        consumer.onDispatchEvent(
                event(EventTypes.DISPATCH_FAILED, "{\"shipmentId\":\"" + shipmentId + "\"}"));

        verify(shipmentService, never()).onNoDriver(any());
    }

    @Test
    void invalidMessageIsRejected() {
        assertThatThrownBy(() -> consumer.onDispatchEvent("{roto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dispatch");
    }
}
