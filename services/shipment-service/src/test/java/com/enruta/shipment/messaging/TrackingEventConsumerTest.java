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
class TrackingEventConsumerTest {

    @Mock private ShipmentService shipmentService;
    @Mock private ProcessedEventStore processedEvents;

    private TrackingEventConsumer consumer;
    private final UUID eventId = UUID.randomUUID();
    private final UUID shipmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new TrackingEventConsumer(shipmentService, processedEvents, new ObjectMapper());
    }

    private String event(String type, String fence) {
        return "{\"eventId\":\""
                + eventId
                + "\",\"eventType\":\""
                + type
                + "\",\"payload\":{\"shipmentId\":\""
                + shipmentId
                + "\",\"fence\":\""
                + fence
                + "\"}}";
    }

    @Test
    void pickupFenceConfirmsPickup() {
        when(processedEvents.markIfNew(eventId, "shipment-tracking")).thenReturn(true);

        consumer.onTrackingEvent(event(EventTypes.TRACKING_GEOFENCE_ENTERED, "PICKUP"));

        verify(shipmentService).onPickupConfirmed(shipmentId);
    }

    @Test
    void dropoffFenceMarksDelivered() {
        when(processedEvents.markIfNew(eventId, "shipment-tracking")).thenReturn(true);

        consumer.onTrackingEvent(event(EventTypes.TRACKING_GEOFENCE_ENTERED, "DROPOFF"));

        verify(shipmentService).onDelivered(shipmentId);
    }

    @Test
    void unknownFenceIsIgnored() {
        when(processedEvents.markIfNew(eventId, "shipment-tracking")).thenReturn(true);

        consumer.onTrackingEvent(event(EventTypes.TRACKING_GEOFENCE_ENTERED, "OTRA"));

        verify(shipmentService, never()).onPickupConfirmed(any());
        verify(shipmentService, never()).onDelivered(any());
    }

    @Test
    void tripStartedIsForwarded() {
        when(processedEvents.markIfNew(eventId, "shipment-tracking")).thenReturn(true);

        consumer.onTrackingEvent(event(EventTypes.TRACKING_TRIP_STARTED, null));

        verify(shipmentService).onTripStarted(shipmentId);
    }

    @Test
    void unknownTypeIsIgnored() {
        when(processedEvents.markIfNew(eventId, "shipment-tracking")).thenReturn(true);

        consumer.onTrackingEvent(event("tracking.otro", null));

        verify(shipmentService, never()).onTripStarted(any());
    }

    @Test
    void duplicatedEventIsSkipped() {
        when(processedEvents.markIfNew(eventId, "shipment-tracking")).thenReturn(false);

        consumer.onTrackingEvent(event(EventTypes.TRACKING_GEOFENCE_ENTERED, "PICKUP"));

        verify(shipmentService, never()).onPickupConfirmed(any());
    }

    @Test
    void invalidMessageIsRejected() {
        assertThatThrownBy(() -> consumer.onTrackingEvent("no-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tracking");
    }
}
