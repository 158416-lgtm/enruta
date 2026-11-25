package com.enruta.dispatch.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enruta.common.error.BusinessException;
import com.enruta.dispatch.domain.DispatchState;
import com.enruta.dispatch.domain.Driver;
import com.enruta.dispatch.domain.Offer;
import com.enruta.dispatch.domain.OfferStatus;
import com.enruta.dispatch.repository.DispatchStateRepository;
import com.enruta.dispatch.repository.DriverRepository;
import com.enruta.dispatch.repository.OfferRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchQueryControllerTest {

    @Mock private DispatchStateRepository states;
    @Mock private OfferRepository offers;
    @Mock private DriverRepository drivers;

    private DispatchQueryController controller;

    @BeforeEach
    void setUp() {
        controller = new DispatchQueryController(states, offers, drivers);
    }

    @Test
    void exposesTheDispatchStateWithItsPendingOffers() {
        UUID shipmentId = UUID.randomUUID();
        UUID knownDriverId = UUID.randomUUID();
        UUID unknownDriverId = UUID.randomUUID();
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);
        state.markOffered(UUID.randomUUID());
        Offer withDriver =
                new Offer(
                        shipmentId,
                        knownDriverId,
                        (short) 1,
                        450,
                        0.9,
                        Instant.parse("2026-01-01T00:00:00Z"));
        Offer withoutDistance = mock(Offer.class);
        when(withoutDistance.getId()).thenReturn(UUID.randomUUID());
        when(withoutDistance.getDriverId()).thenReturn(unknownDriverId);
        when(withoutDistance.getExpiresAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(withoutDistance.getDistanceMeters()).thenReturn(null);

        when(states.findById(shipmentId)).thenReturn(Optional.of(state));
        when(offers.findByShipmentIdAndStatus(shipmentId, OfferStatus.CREATED))
                .thenReturn(List.of(withDriver, withoutDistance));
        when(drivers.findById(knownDriverId))
                .thenReturn(
                        Optional.of(
                                new Driver(knownDriverId, "Ana", "MOTO", 4.9, 4.3449, -74.3674)));
        when(drivers.findById(unknownDriverId)).thenReturn(Optional.empty());

        DispatchQueryController.DispatchStateView view = controller.state(shipmentId);

        assertThat(view.shipmentId()).isEqualTo(shipmentId);
        assertThat(view.phase()).isEqualTo(DispatchState.Phase.OFFERED.name());
        assertThat(view.round()).isEqualTo((short) 0);
        assertThat(view.currentOfferId()).isEqualTo(state.getCurrentOfferId());
        assertThat(view.updatedAt()).isEqualTo(state.getUpdatedAt());
        assertThat(view.pendingOffers()).hasSize(2);
        assertThat(view.pendingOffers().get(0).driverName()).isEqualTo("Ana");
        assertThat(view.pendingOffers().get(0).distanceMeters()).isEqualTo(450);
        assertThat(view.pendingOffers().get(1).driverName()).isEqualTo("Conductor");
        assertThat(view.pendingOffers().get(1).distanceMeters()).isZero();
    }

    @Test
    void unknownShipmentIsNotFound() {
        UUID shipmentId = UUID.randomUUID();
        when(states.findById(shipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.state(shipmentId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("DISPATCH_NOT_FOUND");
    }
}
