package com.enruta.dispatch.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enruta.common.messaging.ProcessedEventStore;
import com.enruta.contracts.EventTypes;
import com.enruta.dispatch.service.DispatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShipmentEventConsumerTest {

    @Mock private DispatchService dispatchService;
    @Mock private ProcessedEventStore processedEvents;

    private ShipmentEventConsumer consumer;
    private UUID shipmentId;

    @BeforeEach
    void setUp() {
        consumer = new ShipmentEventConsumer(dispatchService, processedEvents, new ObjectMapper());
        shipmentId = UUID.randomUUID();
        org.mockito.Mockito.lenient()
                .when(processedEvents.markIfNew(org.mockito.ArgumentMatchers.any(), anyString()))
                .thenReturn(true);
    }

    @Test
    void createdEventStartsTheDispatch() {
        consumer.onShipmentEvent(
                message(
                        EventTypes.SHIPMENT_CREATED,
                        "{\"shipmentId\":\""
                                + shipmentId
                                + "\",\"pickupLat\":4.35,\"pickupLng\":-74.36}"));

        verify(dispatchService).onShipmentCreated(shipmentId, 4.35, -74.36);
    }

    @Test
    void unassignedEventRestartsTheSearch() {
        consumer.onShipmentEvent(
                message(EventTypes.SHIPMENT_UNASSIGNED, "{\"shipmentId\":\"" + shipmentId + "\"}"));

        verify(dispatchService).restart(shipmentId);
    }

    @Test
    void cancelledEventCancelsTheShipment() {
        consumer.onShipmentEvent(
                message(EventTypes.SHIPMENT_CANCELLED, "{\"shipmentId\":\"" + shipmentId + "\"}"));

        verify(dispatchService).cancelShipment(shipmentId);
    }

    @Test
    void deliveredEventFinishesTheDelivery() {
        consumer.onShipmentEvent(
                message(EventTypes.SHIPMENT_DELIVERED, "{\"shipmentId\":\"" + shipmentId + "\"}"));

        verify(dispatchService).finishDelivery(shipmentId);
    }

    @Test
    void failedEventFinishesTheDelivery() {
        consumer.onShipmentEvent(
                message(EventTypes.SHIPMENT_FAILED, "{\"shipmentId\":\"" + shipmentId + "\"}"));

        verify(dispatchService).finishDelivery(shipmentId);
    }

    @Test
    void statusChangedToCancelledCancelsTheShipment() {
        consumer.onShipmentEvent(
                message(
                        EventTypes.SHIPMENT_STATUS_CHANGED,
                        "{\"shipmentId\":\"" + shipmentId + "\",\"status\":\"CANCELLED\"}"));

        verify(dispatchService).cancelShipment(shipmentId);
    }

    @Test
    void otherStatusChangesAreIgnored() {
        consumer.onShipmentEvent(
                message(
                        EventTypes.SHIPMENT_STATUS_CHANGED,
                        "{\"shipmentId\":\"" + shipmentId + "\",\"status\":\"PICKED_UP\"}"));

        verifyNoInteractions(dispatchService);
    }

    @Test
    void unknownEventsAreIgnored() {
        consumer.onShipmentEvent(
                message("shipment.something-else", "{\"shipmentId\":\"" + shipmentId + "\"}"));

        verify(dispatchService, never())
                .onShipmentCreated(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyDouble(),
                        org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void alreadyProcessedEventsAreDeduplicated() {
        when(processedEvents.markIfNew(org.mockito.ArgumentMatchers.any(), anyString()))
                .thenReturn(false);

        consumer.onShipmentEvent(
                message(EventTypes.SHIPMENT_CREATED, "{\"shipmentId\":\"" + shipmentId + "\"}"));

        verifyNoInteractions(dispatchService);
    }

    @Test
    void invalidMessagesAreRejected() {
        assertThatThrownBy(() -> consumer.onShipmentEvent("not-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalido");
    }

    private static String message(String type, String payload) {
        return "{\"eventId\":\""
                + UUID.randomUUID()
                + "\",\"eventType\":\""
                + type
                + "\",\"payload\":"
                + payload
                + "}";
    }
}
