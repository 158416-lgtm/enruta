package com.enruta.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainAccessorsTest {

    @Test
    void offerExposesItsLifecycle() {
        UUID shipmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Offer offer =
                new Offer(
                        shipmentId,
                        driverId,
                        (short) 2,
                        400,
                        0.75,
                        Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(offer.getScore()).isEqualTo(0.75);
        assertThat(offer.getCreatedAt()).isNotNull();
        offer.accept();
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        offer.setStatus(OfferStatus.SUPERSEDED);
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.SUPERSEDED);
    }

    @Test
    void offerStatusKnowsWhichStatesArePending() {
        assertThat(OfferStatus.CREATED.isPending()).isTrue();
        assertThat(OfferStatus.ACCEPTED.isPending()).isFalse();
        assertThat(OfferStatus.REJECTED.isPending()).isFalse();
        assertThat(OfferStatus.EXPIRED.isPending()).isFalse();
        assertThat(OfferStatus.SUPERSEDED.isPending()).isFalse();
    }

    @Test
    void assignmentExposesItsTimestamps() {
        Assignment assignment = new Assignment(UUID.randomUUID(), UUID.randomUUID());
        assertThat(assignment.getCreatedAt()).isNotNull();
        assertThat(assignment.getEndedAt()).isNull();

        assignment.complete();

        assertThat(assignment.getEndedAt()).isNotNull();
    }

    @Test
    void driverExposesItsProfile() {
        Driver driver = new Driver(UUID.randomUUID(), "Ana", "MOTO", 4.9, 4.3449, -74.3674);

        assertThat(driver.getFullName()).isEqualTo("Ana");
        assertThat(driver.getVehicleType()).isEqualTo("MOTO");
        assertThat(driver.getUpdatedAt()).isNotNull();
    }

    @Test
    void dispatchStateExposesItsProgress() {
        UUID shipmentId = UUID.randomUUID();
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);

        assertThat(state.getShipmentId()).isEqualTo(shipmentId);
        assertThat(state.isActive()).isTrue();
        assertThat(state.getUpdatedAt()).isNotNull();
        assertThat(state.getCurrentOfferId()).isNull();

        UUID offerId = UUID.randomUUID();
        state.markOffered(offerId);

        assertThat(state.getCurrentOfferId()).isEqualTo(offerId);
        assertThat(state.isActive()).isTrue();
    }
}
