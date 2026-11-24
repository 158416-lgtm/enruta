package com.enruta.dispatch.service;

import com.enruta.common.error.BusinessException;
import com.enruta.common.outbox.OutboxAppender;
import com.enruta.contracts.EventEnvelope;
import com.enruta.contracts.EventTypes;
import com.enruta.contracts.Topics;
import com.enruta.dispatch.domain.Assignment;
import com.enruta.dispatch.domain.DispatchState;
import com.enruta.dispatch.domain.Driver;
import com.enruta.dispatch.domain.DriverStatus;
import com.enruta.dispatch.domain.Offer;
import com.enruta.dispatch.domain.OfferStatus;
import com.enruta.dispatch.messaging.DispatchEvents;
import com.enruta.dispatch.repository.AssignmentRepository;
import com.enruta.dispatch.repository.DispatchStateRepository;
import com.enruta.dispatch.repository.DriverRepository;
import com.enruta.dispatch.repository.OfferRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corazon del despacho: elige candidatos, emite ofertas con vencimiento y decide quien gana.
 *
 * <p>El orden importa. La cercania se consulta en Redis GEO, el ranking pondera cercania y
 * calificacion, y la aceptacion pasa por dos barreras independientes (Redis y la base).
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    static final int[] RADII_METERS = {1500, 3000, 5000};
    static final int MAX_ROUNDS = RADII_METERS.length;
    private static final int CANDIDATES_PER_ROUND_DEFAULT = 3;

    private static final double AVERAGE_SPEED_MPS = 25_000.0 / 3600.0;

    private final DriverRepository drivers;
    private final OfferRepository offers;
    private final AssignmentRepository assignments;
    private final DispatchStateRepository states;
    private final CandidateFinder finder;
    private final OutboxAppender outbox;
    private final StringRedisTemplate redis;
    private final Duration offerTtl;
    private static final int CANDIDATES_PER_ROUND = CANDIDATES_PER_ROUND_DEFAULT;

    public DispatchService(
            DriverRepository drivers,
            OfferRepository offers,
            AssignmentRepository assignments,
            DispatchStateRepository states,
            CandidateFinder finder,
            OutboxAppender outbox,
            StringRedisTemplate redis,
            @org.springframework.beans.factory.annotation.Value("${enruta.dispatch.offer-ttl:90s}")
                    Duration offerTtl) {
        this.drivers = drivers;
        this.offers = offers;
        this.assignments = assignments;
        this.states = states;
        this.finder = finder;
        this.outbox = outbox;
        this.redis = redis;
        this.offerTtl = offerTtl;
    }

    public record AcceptResult(UUID assignmentId, UUID shipmentId, UUID driverId) {}

    @Transactional
    public void onShipmentCreated(UUID shipmentId, double pickupLat, double pickupLng) {
        if (states.existsById(shipmentId)) {
            log.debug("dispatch ya existe para shipment={}", shipmentId);
            return;
        }
        states.save(new DispatchState(shipmentId, pickupLat, pickupLng));
        offerNext(shipmentId);
    }

    /** Reanuda la busqueda (por ejemplo, tras una cancelacion del conductor). */
    @Transactional
    public void restart(UUID shipmentId) {
        DispatchState state = states.findById(shipmentId).orElse(null);
        if (state == null) {
            return;
        }
        state.setPhase(DispatchState.Phase.SEARCHING);
        offerNext(shipmentId);
    }

    /**
     * Cierra el ciclo: el envio termino (entregado, fallido o cancelado) y el conductor vuelve a
     * estar disponible.
     *
     * <p>Sin esto los conductores se quedan BUSY para siempre y, tras unas cuantas entregas, ya no
     * hay nadie a quien ofertar.
     */
    @Transactional
    public void finishDelivery(UUID shipmentId) {
        assignments
                .findFirstByShipmentIdAndStatus(shipmentId, Assignment.Status.ACTIVE)
                .ifPresent(
                        assignment -> {
                            assignment.complete();
                            drivers.findById(assignment.getDriverId())
                                    .ifPresent(
                                            driver -> {
                                                driver.markAvailable();
                                                if (driver.getCurrentLat() != null
                                                        && driver.getCurrentLng() != null) {
                                                    finder.index(
                                                            driver.getId(),
                                                            driver.getCurrentLat(),
                                                            driver.getCurrentLng());
                                                }
                                            });
                            log.info(
                                    "asignacion cerrada shipment={} driver={}",
                                    shipmentId,
                                    assignment.getDriverId());
                        });
        states.findById(shipmentId).ifPresent(DispatchState::markCompleted);
    }

    @Transactional
    public void cancelShipment(UUID shipmentId) {
        states.findById(shipmentId)
                .ifPresent(
                        state -> {
                            state.markCancelled();
                            offers.findByShipmentIdAndStatus(shipmentId, OfferStatus.CREATED)
                                    .forEach(Offer::supersede);
                        });
        finishDelivery(shipmentId);
    }

    @Transactional
    public void offerNext(UUID shipmentId) {
        DispatchState state =
                states.findById(shipmentId)
                        .orElseThrow(
                                () ->
                                        BusinessException.notFound(
                                                "DISPATCH_NOT_FOUND", "Envio sin despacho"));

        if (state.getPhase() == DispatchState.Phase.ASSIGNED
                || state.getPhase() == DispatchState.Phase.CANCELLED
                || state.getPhase() == DispatchState.Phase.EXHAUSTED) {
            return;
        }

        state.setPhase(DispatchState.Phase.SEARCHING);
        if (state.getRound() == 0) {
            state.nextRound();
        }

        Set<UUID> alreadyOffered = new HashSet<>(offers.findDriverIdsByShipmentId(shipmentId));

        while (state.getRound() <= MAX_ROUNDS) {
            if (offers.countByShipmentAndRound(shipmentId, state.getRound())
                    >= CANDIDATES_PER_ROUND) {
                state.nextRound();
                continue;
            }
            int radius = RADII_METERS[state.getRound() - 1];
            List<CandidateFinder.Candidate> candidates =
                    finder.nearest(state.getPickupLat(), state.getPickupLng(), radius, 8).stream()
                            .filter(candidate -> !alreadyOffered.contains(candidate.driverId()))
                            .limit(CANDIDATES_PER_ROUND * 2L)
                            .toList();

            List<OfferScorer.Ranked> ranked =
                    OfferScorer.rank(candidates, radius, id -> ratingOf(id));

            for (OfferScorer.Ranked rankedCandidate : ranked) {
                Driver driver =
                        drivers.findById(rankedCandidate.candidate().driverId()).orElse(null);
                if (driver == null || driver.getStatus() != DriverStatus.AVAILABLE) {
                    alreadyOffered.add(rankedCandidate.candidate().driverId());
                    continue;
                }
                Offer offer =
                        offers.save(
                                new Offer(
                                        shipmentId,
                                        driver.getId(),
                                        state.getRound(),
                                        rankedCandidate.candidate().distanceMeters(),
                                        rankedCandidate.score(),
                                        Instant.now().plus(offerTtl)));
                state.markOffered(offer.getId());
                publishOffer(offer);
                log.info(
                        "oferta creada shipment={} driver={} round={} distancia={}m",
                        shipmentId,
                        driver.getId(),
                        offer.getRound(),
                        offer.getDistanceMeters());
                return;
            }

            state.nextRound();
        }

        state.markExhausted();
        outbox.append(
                EventEnvelope.of(
                        EventTypes.DISPATCH_FAILED,
                        1,
                        shipmentId,
                        null,
                        new DispatchEvents.DispatchFailed(
                                shipmentId, "sin conductores disponibles")),
                Topics.DISPATCH_EVENTS);
        log.warn("despacho agotado shipment={}", shipmentId);
    }

    @Transactional
    public AcceptResult accept(UUID offerId, UUID callerId, List<String> roles) {
        Offer offer = loadOffer(offerId);
        boolean admin = roles.contains("ADMIN");
        if (!admin && !offer.getDriverId().equals(callerId)) {
            throw new BusinessException("FORBIDDEN", "La oferta no es tuya", HttpStatus.FORBIDDEN);
        }
        if (!offer.getStatus().isPending() || offer.isExpired(Instant.now())) {
            throw BusinessException.conflict("OFFER_EXPIRED", "La oferta ya no esta vigente");
        }

        String claimKey = claimKey(offerId);
        Boolean claimed = redis.opsForValue().setIfAbsent(claimKey, callerId.toString(), offerTtl);
        if (!Boolean.TRUE.equals(claimed)) {
            throw BusinessException.conflict(
                    "OFFER_ALREADY_CLAIMED", "Otro conductor tomo esta oferta");
        }

        try {
            int updated =
                    offers.updateStatusIfCurrent(
                            offerId, OfferStatus.CREATED, OfferStatus.ACCEPTED);
            if (updated == 0) {
                throw BusinessException.conflict("OFFER_NOT_PENDING", "La oferta ya fue resuelta");
            }

            Assignment assignment =
                    assignments.saveAndFlush(
                            new Assignment(offer.getShipmentId(), offer.getDriverId()));

            Driver driver =
                    drivers.findById(offer.getDriverId())
                            .orElseThrow(
                                    () ->
                                            BusinessException.notFound(
                                                    "DRIVER_NOT_FOUND", "Conductor inexistente"));
            driver.markBusy();
            finder.unindex(driver.getId());

            DispatchState state =
                    states.findById(offer.getShipmentId())
                            .orElseThrow(
                                    () ->
                                            BusinessException.notFound(
                                                    "DISPATCH_NOT_FOUND", "Envio sin despacho"));
            state.markAssigned();

            outbox.append(
                    EventEnvelope.of(
                            EventTypes.DISPATCH_ASSIGNED,
                            1,
                            offer.getShipmentId(),
                            null,
                            new DispatchEvents.Assigned(
                                    assignment.getId(),
                                    offer.getShipmentId(),
                                    driver.getId(),
                                    null,
                                    Instant.now())),
                    Topics.DISPATCH_EVENTS);

            log.info(
                    "asignado shipment={} driver={} assignment={}",
                    offer.getShipmentId(),
                    driver.getId(),
                    assignment.getId());
            return new AcceptResult(assignment.getId(), offer.getShipmentId(), driver.getId());
        } catch (RuntimeException ex) {
            redis.delete(claimKey);
            throw ex;
        }
    }

    @Transactional
    public void reject(UUID offerId, UUID callerId, List<String> roles) {
        Offer offer = loadOffer(offerId);
        boolean admin = roles.contains("ADMIN");
        if (!admin && !offer.getDriverId().equals(callerId)) {
            throw new BusinessException("FORBIDDEN", "La oferta no es tuya", HttpStatus.FORBIDDEN);
        }
        if (!offer.getStatus().isPending()) {
            throw BusinessException.conflict("OFFER_NOT_PENDING", "La oferta ya fue resuelta");
        }
        offer.reject();
        states.findById(offer.getShipmentId())
                .ifPresent(s -> s.setPhase(DispatchState.Phase.SEARCHING));
        offerNext(offer.getShipmentId());
    }

    /** Vence ofertas expiradas y sigue con el siguiente candidato. Lo llama el scheduler. */
    @Transactional
    public int expireDueOffers() {
        List<Offer> due = offers.findByStatusAndExpiresAtBefore(OfferStatus.CREATED, Instant.now());
        for (Offer offer : due) {
            offer.expire();
            DispatchState state = states.findById(offer.getShipmentId()).orElse(null);
            if (state == null || !state.isActive()) {
                continue;
            }
            if (!offer.getId().equals(state.getCurrentOfferId())) {
                continue;
            }
            state.setPhase(DispatchState.Phase.SEARCHING);
            offerNext(offer.getShipmentId());
        }
        return due.size();
    }

    @Transactional
    public void cancelAssignment(UUID assignmentId, UUID callerId, List<String> roles) {
        Assignment assignment =
                assignments
                        .findById(assignmentId)
                        .orElseThrow(
                                () ->
                                        BusinessException.notFound(
                                                "ASSIGNMENT_NOT_FOUND", "Asignacion inexistente"));
        boolean admin = roles.contains("ADMIN");
        if (!admin && !assignment.getDriverId().equals(callerId)) {
            throw new BusinessException(
                    "FORBIDDEN", "La asignacion no es tuya", HttpStatus.FORBIDDEN);
        }
        if (assignment.getStatus() != Assignment.Status.ACTIVE) {
            throw BusinessException.conflict("ASSIGNMENT_NOT_ACTIVE", "La asignacion ya termino");
        }
        assignment.cancel();

        drivers.findById(assignment.getDriverId())
                .ifPresent(
                        driver -> {
                            driver.markAvailable();
                            if (driver.getCurrentLat() != null && driver.getCurrentLng() != null) {
                                finder.index(
                                        driver.getId(),
                                        driver.getCurrentLat(),
                                        driver.getCurrentLng());
                            }
                        });

        states.findById(assignment.getShipmentId())
                .ifPresent(state -> state.setPhase(DispatchState.Phase.CANCELLED));

        outbox.append(
                EventEnvelope.of(
                        EventTypes.DISPATCH_ASSIGNMENT_CANCELLED,
                        1,
                        assignment.getShipmentId(),
                        null,
                        new DispatchEvents.AssignmentCancelled(
                                assignment.getId(),
                                assignment.getShipmentId(),
                                assignment.getDriverId())),
                Topics.DISPATCH_EVENTS);
    }

    private void publishOffer(Offer offer) {
        int etaSeconds = (int) Math.max(60, offer.getDistanceMeters() / AVERAGE_SPEED_MPS);
        outbox.append(
                EventEnvelope.of(
                        EventTypes.DISPATCH_OFFER_CREATED,
                        1,
                        offer.getShipmentId(),
                        null,
                        new DispatchEvents.OfferCreated(
                                offer.getId(),
                                offer.getShipmentId(),
                                offer.getDriverId(),
                                offer.getExpiresAt(),
                                offer.getRound(),
                                offer.getDistanceMeters(),
                                etaSeconds)),
                Topics.DISPATCH_EVENTS);
    }

    private double ratingOf(UUID driverId) {
        return drivers.findById(driverId).map(Driver::getRating).orElse(0.0);
    }

    private Offer loadOffer(UUID offerId) {
        return offers.findById(offerId)
                .orElseThrow(
                        () -> BusinessException.notFound("OFFER_NOT_FOUND", "Oferta inexistente"));
    }

    private String claimKey(UUID offerId) {
        return "offer:" + offerId + ":claim";
    }
}
