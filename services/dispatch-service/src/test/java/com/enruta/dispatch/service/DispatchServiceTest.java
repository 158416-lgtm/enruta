package com.enruta.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.common.outbox.OutboxAppender;
import com.enruta.dispatch.domain.Assignment;
import com.enruta.dispatch.domain.DispatchState;
import com.enruta.dispatch.domain.Driver;
import com.enruta.dispatch.domain.DriverStatus;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

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
                        java.time.Duration.ofSeconds(90));
    }

    @Test
    void finishingADeliveryFreesTheDriver() {
        UUID shipmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Assignment assignment = new Assignment(shipmentId, driverId);
        Driver driver = new Driver(driverId, "Ana", "MOTO", 4.9, 4.3449, -74.3674);
        driver.markBusy();
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);

        when(assignments.findFirstByShipmentIdAndStatus(shipmentId, Assignment.Status.ACTIVE))
                .thenReturn(Optional.of(assignment));
        when(drivers.findById(driverId)).thenReturn(Optional.of(driver));
        when(states.findById(shipmentId)).thenReturn(Optional.of(state));

        service.finishDelivery(shipmentId);

        assertThat(assignment.getStatus()).isEqualTo(Assignment.Status.COMPLETED);
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(state.getPhase()).isEqualTo(DispatchState.Phase.COMPLETED);
        verify(finder).index(driverId, 4.3449, -74.3674);
    }

    @Test
    void finishingWithoutActiveAssignmentIsHarmless() {
        UUID shipmentId = UUID.randomUUID();
        when(assignments.findFirstByShipmentIdAndStatus(shipmentId, Assignment.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(states.findById(shipmentId)).thenReturn(Optional.empty());

        service.finishDelivery(shipmentId);

        verify(drivers, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void aRoundOffersToTheThreeBestCandidatesAtOnce() {
        UUID shipmentId = UUID.randomUUID();
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);
        when(states.findById(shipmentId)).thenReturn(Optional.of(state));
        when(offers.findDriverIdsByShipmentId(shipmentId)).thenReturn(List.of());
        when(offers.countByShipmentAndRound(shipmentId, (short) 1)).thenReturn(0L);

        List<Driver> fleet =
                List.of(
                        new Driver(UUID.randomUUID(), "Ana", "MOTO", 4.9, 4.3449, -74.3674),
                        new Driver(UUID.randomUUID(), "Beto", "MOTO", 4.7, 4.3451, -74.3676),
                        new Driver(UUID.randomUUID(), "Carla", "BICI", 4.5, 4.3452, -74.3677));
        when(finder.nearest(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(
                        List.of(
                                new CandidateFinder.Candidate(fleet.get(0).getId(), 30),
                                new CandidateFinder.Candidate(fleet.get(1).getId(), 90),
                                new CandidateFinder.Candidate(fleet.get(2).getId(), 150)));
        for (Driver driver : fleet) {
            when(drivers.findById(driver.getId())).thenReturn(Optional.of(driver));
        }
        when(offers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.offerNext(shipmentId);

        verify(offers, times(3)).save(any());
        verify(outbox, times(3)).append(any(), anyString());
        assertThat(state.getPhase()).isEqualTo(DispatchState.Phase.OFFERED);
    }

    @Test
    void acceptingAnOfferSupersedesTheOtherOffers() {
        UUID shipmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Offer offer =
                new Offer(shipmentId, driverId, (short) 1, 30, 0.9, Instant.now().plusSeconds(60));
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);
        Driver driver = new Driver(driverId, "Ana", "MOTO", 4.9, 4.3449, -74.3674);

        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(offers.updateStatusIfCurrent(offer.getId(), OfferStatus.CREATED, OfferStatus.ACCEPTED))
                .thenReturn(1);
        when(assignments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(drivers.findById(driverId)).thenReturn(Optional.of(driver));
        when(states.findById(shipmentId)).thenReturn(Optional.of(state));

        service.accept(offer.getId(), driverId, List.of("DRIVER"));

        verify(offers)
                .updateStatusForShipment(shipmentId, OfferStatus.CREATED, OfferStatus.SUPERSEDED);
        assertThat(state.getPhase()).isEqualTo(DispatchState.Phase.ASSIGNED);
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.BUSY);
    }

    @Test
    void rejectingTheLastOfferStartsANewRound() {
        UUID shipmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Offer offer =
                new Offer(shipmentId, driverId, (short) 1, 30, 0.9, Instant.now().plusSeconds(60));
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);

        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(offers.countByShipmentIdAndStatus(shipmentId, OfferStatus.CREATED)).thenReturn(0L);
        when(states.findById(shipmentId)).thenReturn(Optional.of(state));
        when(offers.findDriverIdsByShipmentId(shipmentId)).thenReturn(List.of(driverId));
        when(offers.countByShipmentAndRound(shipmentId, (short) 1)).thenReturn(1L);
        when(offers.countByShipmentAndRound(shipmentId, (short) 2)).thenReturn(0L);
        when(offers.countByShipmentAndRound(shipmentId, (short) 3)).thenReturn(0L);
        when(finder.nearest(anyDouble(), anyDouble(), anyInt(), anyInt())).thenReturn(List.of());

        service.reject(offer.getId(), driverId, List.of("DRIVER"));

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.REJECTED);
        assertThat(state.getPhase()).isEqualTo(DispatchState.Phase.EXHAUSTED);
        verify(outbox, org.mockito.Mockito.atLeastOnce()).append(any(), anyString());
    }

    @Test
    void rejectingOneOfferKeepsTheOthersAlive() {
        UUID shipmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Offer offer =
                new Offer(shipmentId, driverId, (short) 1, 30, 0.9, Instant.now().plusSeconds(60));

        when(offers.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(offers.countByShipmentIdAndStatus(shipmentId, OfferStatus.CREATED)).thenReturn(2L);

        service.reject(offer.getId(), driverId, List.of("DRIVER"));

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.REJECTED);
        verify(offers, times(0))
                .countByShipmentAndRound(any(), org.mockito.ArgumentMatchers.anyShort());
    }

    @Test
    void expiringTheLastOfferAdvancesTheShipment() {
        UUID shipmentId = UUID.randomUUID();
        Offer expired =
                new Offer(
                        shipmentId,
                        UUID.randomUUID(),
                        (short) 1,
                        30,
                        0.9,
                        Instant.now().minusSeconds(5));
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);

        when(offers.findByStatusAndExpiresAtBefore(eq(OfferStatus.CREATED), any(Instant.class)))
                .thenReturn(List.of(expired));
        when(states.findById(shipmentId)).thenReturn(Optional.of(state));
        when(offers.countByShipmentIdAndStatus(shipmentId, OfferStatus.CREATED)).thenReturn(0L);
        when(offers.findDriverIdsByShipmentId(shipmentId))
                .thenReturn(List.of(expired.getDriverId()));
        when(offers.countByShipmentAndRound(any(), org.mockito.ArgumentMatchers.anyShort()))
                .thenReturn(3L);

        int processed = service.expireDueOffers();

        assertThat(processed).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo(OfferStatus.EXPIRED);
        assertThat(state.getPhase()).isEqualTo(DispatchState.Phase.EXHAUSTED);
    }

    @Test
    void cancellingAnAssignmentFreesTheDriver() {
        UUID shipmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Assignment assignment = new Assignment(shipmentId, driverId);
        Driver driver = new Driver(driverId, "Ana", "MOTO", 4.9, 4.3449, -74.3674);
        driver.markBusy();
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);

        when(assignments.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(drivers.findById(driverId)).thenReturn(Optional.of(driver));
        when(states.findById(shipmentId)).thenReturn(Optional.of(state));

        service.cancelAssignment(assignment.getId(), driverId, List.of("DRIVER"));

        assertThat(assignment.getStatus()).isEqualTo(Assignment.Status.CANCELLED);
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(state.getPhase()).isEqualTo(DispatchState.Phase.CANCELLED);
        verify(finder).index(driverId, 4.3449, -74.3674);
        verify(outbox).append(any(), anyString());
    }

    @Test
    void restartResumesTheSearch() {
        UUID shipmentId = UUID.randomUUID();
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);
        state.markCancelled();

        when(states.findById(shipmentId)).thenReturn(Optional.of(state));
        when(offers.findDriverIdsByShipmentId(shipmentId)).thenReturn(List.of());
        when(offers.countByShipmentAndRound(any(), org.mockito.ArgumentMatchers.anyShort()))
                .thenReturn(0L);
        when(finder.nearest(anyDouble(), anyDouble(), anyInt(), anyInt())).thenReturn(List.of());

        service.restart(shipmentId);

        verify(finder, org.mockito.Mockito.atLeastOnce())
                .nearest(anyDouble(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    void cancellingAShipmentSupersedesItsOffers() {
        UUID shipmentId = UUID.randomUUID();
        Offer pending =
                new Offer(
                        shipmentId,
                        UUID.randomUUID(),
                        (short) 1,
                        30,
                        0.9,
                        Instant.now().plusSeconds(60));
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);

        when(states.findById(shipmentId)).thenReturn(Optional.of(state));
        when(offers.findByShipmentIdAndStatus(shipmentId, OfferStatus.CREATED))
                .thenReturn(List.of(pending));
        when(assignments.findFirstByShipmentIdAndStatus(shipmentId, Assignment.Status.ACTIVE))
                .thenReturn(Optional.empty());

        service.cancelShipment(shipmentId);

        assertThat(pending.getStatus()).isEqualTo(OfferStatus.SUPERSEDED);
        assertThat(state.getPhase()).isEqualTo(DispatchState.Phase.COMPLETED);
    }
}
