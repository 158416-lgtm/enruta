package com.enruta.shipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.common.error.BusinessException;
import com.enruta.common.outbox.OutboxAppender;
import com.enruta.contracts.EventEnvelope;
import com.enruta.contracts.EventTypes;
import com.enruta.shipment.domain.Shipment;
import com.enruta.shipment.domain.ShipmentStatus;
import com.enruta.shipment.repository.ShipmentRepository;
import com.enruta.shipment.repository.ShipmentStatusHistoryRepository;
import com.enruta.shipment.web.CreateShipmentRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock private ShipmentRepository shipments;
    @Mock private ShipmentStatusHistoryRepository history;
    @Mock private OutboxAppender outbox;

    @Captor private ArgumentCaptor<EventEnvelope<?>> eventCaptor;

    private ShipmentService service;

    @BeforeEach
    void setUp() {
        service = new ShipmentService(shipments, history, outbox);
    }

    @Test
    void createPersistsShipmentHistoryAndEvent() {
        UUID customer = UUID.randomUUID();
        when(shipments.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

        var view = service.create(customer, "cliente@test", validRequest());

        assertThat(view.status()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(view.pickupPin()).hasSize(4).matches("\\d{4}");
        verify(history).save(any());
        verify(outbox).append(eventCaptor.capture(), eq("shipment.events"));
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(EventTypes.SHIPMENT_CREATED);
        assertThat(eventCaptor.getValue().aggregateId()).isEqualTo(view.id());
        assertThat(eventCaptor.getValue().payload()).isInstanceOf(ShipmentEventPayload.class);
    }

    @Test
    void cancelByOwnerIsAllowed() {
        UUID customer = UUID.randomUUID();
        Shipment shipment = shipmentFor(customer, null, ShipmentStatus.CREATED);
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        var view = service.cancel(shipment.getId(), customer, List.of("CLIENT"));

        assertThat(view.status()).isEqualTo(ShipmentStatus.CANCELLED);
    }

    @Test
    void cancelByStrangerIsForbidden() {
        Shipment shipment = shipmentFor(UUID.randomUUID(), null, ShipmentStatus.CREATED);
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        assertThatThrownBy(
                        () ->
                                service.cancel(
                                        shipment.getId(), UUID.randomUUID(), List.of("CLIENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void customerCannotCancelAfterPickup() {
        UUID customer = UUID.randomUUID();
        Shipment shipment = shipmentFor(customer, UUID.randomUUID(), ShipmentStatus.PICKED_UP);
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        assertThatThrownBy(() -> service.cancel(shipment.getId(), customer, List.of("CLIENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("CANCEL_NOT_ALLOWED");
    }

    @Test
    void adminCanCancelAfterPickup() {
        Shipment shipment =
                shipmentFor(UUID.randomUUID(), UUID.randomUUID(), ShipmentStatus.IN_TRANSIT);
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        var view = service.cancel(shipment.getId(), UUID.randomUUID(), List.of("ADMIN"));

        assertThat(view.status()).isEqualTo(ShipmentStatus.CANCELLED);
    }

    @Test
    void invalidTransitionReturnsConflict() {
        Shipment shipment = shipmentFor(UUID.randomUUID(), null, ShipmentStatus.CREATED);

        assertThatThrownBy(
                        () ->
                                service.transition(
                                        shipment,
                                        ShipmentStatus.DELIVERED,
                                        UUID.randomUUID(),
                                        "salto invalido"))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getStatus())
                                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void assignOnlyWorksFromPendingStates() {
        Shipment shipment = shipmentFor(UUID.randomUUID(), null, ShipmentStatus.DELIVERED);
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        assertThatThrownBy(
                        () ->
                                service.assign(
                                        shipment.getId(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
        verify(history, never()).save(any());
    }

    private CreateShipmentRequest validRequest() {
        return new CreateShipmentRequest(
                new CreateShipmentRequest.Coordinate(4.3449, -74.3674, "Parque Principal"),
                new CreateShipmentRequest.Coordinate(4.3374, -74.3600, "Terminal"),
                null);
    }

    private Shipment shipmentFor(UUID customer, UUID driver, ShipmentStatus status) {
        Shipment shipment =
                new Shipment(
                        customer,
                        "cliente@test",
                        4.34,
                        -74.36,
                        4.33,
                        -74.35,
                        "origen",
                        "destino",
                        "1234");
        if (status == ShipmentStatus.CREATED) {
            return shipment;
        }
        shipment.assignTo(driver == null ? UUID.randomUUID() : driver);
        if (status == ShipmentStatus.ASSIGNED) {
            return shipment;
        }
        shipment.transitionTo(ShipmentStatus.PICKED_UP);
        if (status == ShipmentStatus.PICKED_UP) {
            return shipment;
        }
        shipment.transitionTo(ShipmentStatus.IN_TRANSIT);
        if (status == ShipmentStatus.IN_TRANSIT) {
            return shipment;
        }
        shipment.transitionTo(ShipmentStatus.DELIVERED);
        return shipment;
    }

    // --- eventos que llegan de otros servicios ---

    @Test
    void dispatchAssignedMovesTheShipmentToAssigned() {
        UUID customer = UUID.randomUUID();
        UUID driver = UUID.randomUUID();
        Shipment shipment = shipmentFor(customer, null, ShipmentStatus.CREATED);
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        service.onDispatchAssigned(shipment.getId(), driver);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.ASSIGNED);
        assertThat(shipment.getDriverId()).isEqualTo(driver);
        verify(history).save(any());
        verify(outbox).append(any(), eq("shipment.events"));
    }

    @Test
    void repeatedDispatchAssignedIsIdempotent() {
        UUID customer = UUID.randomUUID();
        UUID driver = UUID.randomUUID();
        Shipment shipment = shipmentFor(customer, driver, ShipmentStatus.ASSIGNED);
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        service.onDispatchAssigned(shipment.getId(), driver);

        verify(history, never()).save(any());
        verify(outbox, never()).append(any(), anyString());
    }

    @Test
    void driverCancellationReturnsTheShipmentToCreated() {
        Shipment shipment =
                shipmentFor(UUID.randomUUID(), UUID.randomUUID(), ShipmentStatus.ASSIGNED);
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        service.onDriverCancelled(shipment.getId());

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(shipment.getDriverId()).isNull();
        verify(outbox).append(any(), eq("shipment.events"));
    }

    @Test
    void noDriverMarksTheShipment() {
        Shipment shipment = shipmentFor(UUID.randomUUID(), null, ShipmentStatus.CREATED);
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        service.onNoDriver(shipment.getId());

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.NO_DRIVER);
    }

    @Test
    void pickupConfirmationMovesToPickedUp() {
        Shipment shipment =
                shipmentFor(UUID.randomUUID(), UUID.randomUUID(), ShipmentStatus.ASSIGNED);
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        service.onPickupConfirmed(shipment.getId());

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PICKED_UP);
    }

    @Test
    void tripStartsOnlyAfterPickup() {
        Shipment shipment =
                shipmentFor(UUID.randomUUID(), UUID.randomUUID(), ShipmentStatus.PICKED_UP);
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        service.onTripStarted(shipment.getId());

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void deliveryPublishesTheEventAndTheNotificationCommand() {
        Shipment shipment =
                shipmentFor(UUID.randomUUID(), UUID.randomUUID(), ShipmentStatus.IN_TRANSIT);
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));

        service.onDelivered(shipment.getId());

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        // status-changed (transicion) + delivered + notification.requested
        verify(outbox, times(3)).append(eventCaptor.capture(), anyString());
        assertThat(eventCaptor.getAllValues())
                .extracting(EventEnvelope::eventType)
                .contains(
                        "shipment.status-changed", "shipment.delivered", "notification.requested");
    }
}
