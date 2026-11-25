package com.enruta.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enruta.common.error.BusinessException;
import com.enruta.common.outbox.OutboxAppender;
import com.enruta.dispatch.domain.Assignment;
import com.enruta.dispatch.domain.DispatchState;
import com.enruta.dispatch.domain.Driver;
import com.enruta.dispatch.domain.Offer;
import com.enruta.dispatch.domain.OfferStatus;
import com.enruta.dispatch.repository.AssignmentRepository;
import com.enruta.dispatch.repository.DispatchStateRepository;
import com.enruta.dispatch.repository.DriverRepository;
import com.enruta.dispatch.repository.OfferRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class DispatchServiceCoverageTest {

    @Mock private DriverRepository drivers;
    @Mock private OfferRepository offers;
    @Mock private AssignmentRepository assignments;
    @Mock private DispatchStateRepository states;
    @Mock private CandidateFinder finder;
    @Mock private OutboxAppender outbox;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private DispatchService service;

    @BeforeEach
    void setUp() {
        service =
                new DispatchService(
                        drivers,
                        offers,
                        assignments,
                        states,
                        finder,
                        outbox,
                        redis,
                        Duration.ofSeconds(90));
    }

    @Test
    void onShipmentCreatedIgnoresShipmentsThatAlreadyHaveADispatch() {
        UUID shipmentId = UUID.randomUUID();
        when(states.existsById(shipmentId)).thenReturn(true);

        service.onShipmentCreated(shipmentId, 4.3449, -74.3674);

        verify(states, never()).save(any());
        verifyNoInteractions(finder, offers);
    }

    @Test
    void onShipmentCreatedStoresTheStateAndOffersTheFirstRound() {
        UUID shipmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);
        Driver driver = new Driver(driverId, "Ana", "MOTO", 4.9, 4.3449, -74.3674);

        when(states.existsById(shipmentId)).thenReturn(false);
        when(states.findById(shipmentId)).thenReturn(Optional.of(state));
        when(offers.findDriverIdsByShipmentId(shipmentId)).thenReturn(List.of());
        when(offers.countByShipmentAndRound(shipmentId, (short) 1)).thenReturn(0L);
        when(finder.nearest(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of(new CandidateFinder.Candidate(driverId, 30)));
        when(drivers.findById(driverId)).thenReturn(Optional.of(driver));
        when(offers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.onShipmentCreated(shipmentId, 4.3449, -74.3674);

        verify(states).save(any(DispatchState.class));
        verify(offers, times(1)).save(any());
        assertThat(state.getPhase()).isEqualTo(DispatchState.Phase.OFFERED);
    }

    @Test
    void restartingAnUnknownShipmentDoesNothing() {
        UUID shipmentId = UUID.randomUUID();
        when(states.findById(shipmentId)).thenReturn(Optional.empty());

        service.restart(shipmentId);

        verifyNoInteractions(finder, offers);
    }

    @Test
    void offeringForAMissingDispatchFails() {
        UUID shipmentId = UUID.randomUUID();
        when(states.findById(shipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.offerNext(shipmentId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("DISPATCH_NOT_FOUND");
    }

    @Test
    void offeringIsSkippedForTerminalStates() {
        UUID shipmentId = UUID.randomUUID();
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);
        state.markAssigned();
        when(states.findById(shipmentId)).thenReturn(Optional.of(state));

        service.offerNext(shipmentId);

        verifyNoInteractions(finder, offers);
    }

    @Test
    void offerRoundSkipsAlreadyOfferedCandidates() {
        UUID shipmentId = UUID.randomUUID();
        UUID alreadyOffered = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);
        Driver driver = new Driver(driverId, "Ana", "MOTO", 4.9, 4.3449, -74.3674);

        when(states.findById(shipmentId)).thenReturn(Optional.of(state));
        when(offers.findDriverIdsByShipmentId(shipmentId)).thenReturn(List.of(alreadyOffered));
        when(offers.countByShipmentAndRound(shipmentId, (short) 1)).thenReturn(0L);
        when(finder.nearest(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(
                        List.of(
                                new CandidateFinder.Candidate(alreadyOffered, 10),
                                new CandidateFinder.Candidate(driverId, 20)));
        when(drivers.findById(driverId)).thenReturn(Optional.of(driver));
        when(offers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.offerNext(shipmentId);

        verify(offers, times(1)).save(any());
        assertThat(state.getPhase()).isEqualTo(DispatchState.Phase.OFFERED);
    }

    @Test
    void offerRoundSkipsDriversThatAreMissingOrBusy() {
        UUID shipmentId = UUID.randomUUID();
        UUID missingDriver = UUID.randomUUID();
        UUID busyDriverId = UUID.randomUUID();
        UUID availableDriverId = UUID.randomUUID();
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);
        Driver busy = new Driver(busyDriverId, "Beto", "MOTO", 4.7, 4.3451, -74.3676);
        busy.markBusy();
        Driver available = new Driver(availableDriverId, "Carla", "BICI", 4.5, 4.3452, -74.3677);

        when(states.findById(shipmentId)).thenReturn(Optional.of(state));
        when(offers.findDriverIdsByShipmentId(shipmentId)).thenReturn(List.of());
        when(offers.countByShipmentAndRound(shipmentId, (short) 1)).thenReturn(0L);
        when(finder.nearest(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(
                        List.of(
                                new CandidateFinder.Candidate(missingDriver, 10),
                                new CandidateFinder.Candidate(busyDriverId, 20),
                                new CandidateFinder.Candidate(availableDriverId, 30)));
        when(drivers.findById(missingDriver)).thenReturn(Optional.empty());
        when(drivers.findById(busyDriverId)).thenReturn(Optional.of(busy));
        when(drivers.findById(availableDriverId)).thenReturn(Optional.of(available));
        when(offers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.offerNext(shipmentId);

        verify(offers, times(1)).save(any());
        assertThat(state.getPhase()).isEqualTo(DispatchState.Phase.OFFERED);
    }

    @Test
    void offerRoundStopsWhenTheSlotsAreFilled() {
        UUID shipmentId = UUID.randomUUID();
        UUID firstDriverId = UUID.randomUUID();
        UUID secondDriverId = UUID.randomUUID();
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);
        Driver first = new Driver(firstDriverId, "Ana", "MOTO", 4.9, 4.3449, -74.3674);
        Driver second = new Driver(secondDriverId, "Beto", "MOTO", 4.7, 4.3451, -74.3676);

        when(states.findById(shipmentId)).thenReturn(Optional.of(state));
        when(offers.findDriverIdsByShipmentId(shipmentId)).thenReturn(List.of());
        when(offers.countByShipmentAndRound(shipmentId, (short) 1)).thenReturn(2L);
        when(finder.nearest(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(
                        List.of(
                                new CandidateFinder.Candidate(firstDriverId, 10),
                                new CandidateFinder.Candidate(secondDriverId, 20)));
        when(drivers.findById(firstDriverId)).thenReturn(Optional.of(first));
        when(drivers.findById(secondDriverId)).thenReturn(Optional.of(second));
        when(offers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.offerNext(shipmentId);

        verify(offers, times(1)).save(any());
        assertThat(state.getPhase()).isEqualTo(DispatchState.Phase.OFFERED);
    }

    @Test
    void fullRoundsAreSkippedAndTheDispatchIsExhausted() {
        UUID shipmentId = UUID.randomUUID();
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);

        when(states.findById(shipmentId)).thenReturn(Optional.of(state));
        when(offers.findDriverIdsByShipmentId(shipmentId)).thenReturn(List.of());
        when(offers.countByShipmentAndRound(shipmentId, (short) 1)).thenReturn(3L);
        when(offers.countByShipmentAndRound(shipmentId, (short) 2)).thenReturn(0L);
        when(offers.countByShipmentAndRound(shipmentId, (short) 3)).thenReturn(0L);
        when(finder.nearest(anyDouble(), anyDouble(), anyInt(), anyInt())).thenReturn(List.of());

        service.offerNext(shipmentId);

        assertThat(state.getPhase()).isEqualTo(DispatchState.Phase.EXHAUSTED);
        verify(outbox).append(any(), anyString());
    }

    @Test
    void acceptingAnotherDriversOfferIsForbidden() {
        Offer offer =
                new Offer(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        (short) 1,
                        30,
                        0.9,
                        Instant.now().plusSeconds(60));
        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(
                        () -> service.accept(offer.getId(), UUID.randomUUID(), List.of("DRIVER")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void acceptingAnExpiredOfferFails() {
        UUID driverId = UUID.randomUUID();
        Offer offer =
                new Offer(
                        UUID.randomUUID(),
                        driverId,
                        (short) 1,
                        30,
                        0.9,
                        Instant.now().minusSeconds(5));
        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.accept(offer.getId(), driverId, List.of("DRIVER")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("OFFER_EXPIRED");
    }

    @Test
    void acceptingAnOfferResolvedElsewhereReleasesTheRedisClaim() {
        UUID driverId = UUID.randomUUID();
        Offer offer = pendingOffer(driverId);
        stubClaim(offer, driverId);
        when(offers.updateStatusIfCurrent(offer.getId(), OfferStatus.CREATED, OfferStatus.ACCEPTED))
                .thenReturn(0);

        assertThatThrownBy(() -> service.accept(offer.getId(), driverId, List.of("DRIVER")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("OFFER_NOT_PENDING");
        verify(redis).delete(claimKey(offer));
    }

    @Test
    void acceptingAnAlreadyAssignedShipmentReleasesTheRedisClaim() {
        UUID driverId = UUID.randomUUID();
        Offer offer = pendingOffer(driverId);
        stubClaim(offer, driverId);
        when(offers.updateStatusIfCurrent(offer.getId(), OfferStatus.CREATED, OfferStatus.ACCEPTED))
                .thenReturn(1);
        when(assignments.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_assignment_active"));

        assertThatThrownBy(() -> service.accept(offer.getId(), driverId, List.of("DRIVER")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("ASSIGNMENT_TAKEN");
        verify(redis).delete(claimKey(offer));
    }

    @Test
    void acceptingAnOfferForAMissingDriverReleasesTheRedisClaim() {
        UUID driverId = UUID.randomUUID();
        Offer offer = pendingOffer(driverId);
        stubClaim(offer, driverId);
        when(offers.updateStatusIfCurrent(offer.getId(), OfferStatus.CREATED, OfferStatus.ACCEPTED))
                .thenReturn(1);
        when(assignments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(drivers.findById(driverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(offer.getId(), driverId, List.of("DRIVER")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("DRIVER_NOT_FOUND");
        verify(redis).delete(claimKey(offer));
    }

    @Test
    void acceptingAnOfferForAMissingDispatchReleasesTheRedisClaim() {
        UUID shipmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Offer offer = pendingOffer(shipmentId, driverId);
        stubClaim(offer, driverId);
        when(offers.updateStatusIfCurrent(offer.getId(), OfferStatus.CREATED, OfferStatus.ACCEPTED))
                .thenReturn(1);
        when(assignments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(drivers.findById(driverId))
                .thenReturn(
                        Optional.of(new Driver(driverId, "Ana", "MOTO", 4.9, 4.3449, -74.3674)));
        when(states.findById(shipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(offer.getId(), driverId, List.of("DRIVER")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("DISPATCH_NOT_FOUND");
        verify(redis).delete(claimKey(offer));
    }

    @Test
    void rejectingAnotherDriversOfferIsForbidden() {
        Offer offer = pendingOffer(UUID.randomUUID());
        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(
                        () -> service.reject(offer.getId(), UUID.randomUUID(), List.of("DRIVER")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void rejectingAnAlreadyResolvedOfferFails() {
        UUID driverId = UUID.randomUUID();
        Offer offer = pendingOffer(driverId);
        offer.accept();
        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.reject(offer.getId(), driverId, List.of("DRIVER")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("OFFER_NOT_PENDING");
    }

    @Test
    void rejectingAMissingOfferFails() {
        UUID offerId = UUID.randomUUID();
        when(offers.findById(offerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(offerId, UUID.randomUUID(), List.of("ADMIN")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("OFFER_NOT_FOUND");
    }

    @Test
    void expiringOffersSkipsMissingOrInactiveDispatches() {
        UUID missingShipment = UUID.randomUUID();
        UUID cancelledShipment = UUID.randomUUID();
        Offer first = expiredOffer(missingShipment);
        Offer second = expiredOffer(cancelledShipment);
        DispatchState cancelled = new DispatchState(cancelledShipment, 4.3449, -74.3674);
        cancelled.markCancelled();

        when(offers.findByStatusAndExpiresAtBefore(eq(OfferStatus.CREATED), any(Instant.class)))
                .thenReturn(List.of(first, second));
        when(states.findById(missingShipment)).thenReturn(Optional.empty());
        when(states.findById(cancelledShipment)).thenReturn(Optional.of(cancelled));

        int processed = service.expireDueOffers();

        assertThat(processed).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(OfferStatus.EXPIRED);
        assertThat(second.getStatus()).isEqualTo(OfferStatus.EXPIRED);
        verifyNoInteractions(finder);
    }

    @Test
    void expiringAnOfferDoesNothingWhileAnotherOfferIsStillAlive() {
        UUID shipmentId = UUID.randomUUID();
        Offer expired = expiredOffer(shipmentId);
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);
        state.markOffered(UUID.randomUUID());

        when(offers.findByStatusAndExpiresAtBefore(eq(OfferStatus.CREATED), any(Instant.class)))
                .thenReturn(List.of(expired));
        when(states.findById(shipmentId)).thenReturn(Optional.of(state));
        when(offers.countByShipmentIdAndStatus(shipmentId, OfferStatus.CREATED)).thenReturn(1L);

        int processed = service.expireDueOffers();

        assertThat(processed).isEqualTo(1);
        verify(finder, never()).nearest(anyDouble(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    void expiringWithoutDueOffersIsANoOp() {
        when(offers.findByStatusAndExpiresAtBefore(eq(OfferStatus.CREATED), any(Instant.class)))
                .thenReturn(List.of());

        assertThat(service.expireDueOffers()).isZero();
        verifyNoInteractions(states, finder);
    }

    @Test
    void cancellingAMissingAssignmentFails() {
        UUID assignmentId = UUID.randomUUID();
        when(assignments.findById(assignmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.cancelAssignment(
                                        assignmentId, UUID.randomUUID(), List.of("ADMIN")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("ASSIGNMENT_NOT_FOUND");
    }

    @Test
    void cancellingAnotherDriversAssignmentIsForbidden() {
        Assignment assignment = new Assignment(UUID.randomUUID(), UUID.randomUUID());
        when(assignments.findById(assignment.getId())).thenReturn(Optional.of(assignment));

        assertThatThrownBy(
                        () ->
                                service.cancelAssignment(
                                        assignment.getId(), UUID.randomUUID(), List.of("DRIVER")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void cancellingAFinishedAssignmentFails() {
        UUID driverId = UUID.randomUUID();
        Assignment assignment = new Assignment(UUID.randomUUID(), driverId);
        assignment.complete();
        when(assignments.findById(assignment.getId())).thenReturn(Optional.of(assignment));

        assertThatThrownBy(
                        () ->
                                service.cancelAssignment(
                                        assignment.getId(), driverId, List.of("DRIVER")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("ASSIGNMENT_NOT_ACTIVE");
    }

    private Offer pendingOffer(UUID driverId) {
        return pendingOffer(UUID.randomUUID(), driverId);
    }

    private Offer pendingOffer(UUID shipmentId, UUID driverId) {
        Offer offer =
                new Offer(shipmentId, driverId, (short) 1, 30, 0.9, Instant.now().plusSeconds(60));
        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));
        return offer;
    }

    private void stubClaim(Offer offer, UUID driverId) {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(
                        eq(claimKey(offer)), eq(driverId.toString()), any(Duration.class)))
                .thenReturn(true);
    }

    private static String claimKey(Offer offer) {
        return "offer:" + offer.getId() + ":claim";
    }

    private Offer expiredOffer(UUID shipmentId) {
        return new Offer(
                shipmentId, UUID.randomUUID(), (short) 1, 30, 0.9, Instant.now().minusSeconds(5));
    }
}
