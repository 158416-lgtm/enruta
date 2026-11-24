package com.enruta.dispatch.web;

import com.enruta.common.error.BusinessException;
import com.enruta.dispatch.domain.DispatchState;
import com.enruta.dispatch.domain.Driver;
import com.enruta.dispatch.domain.OfferStatus;
import com.enruta.dispatch.repository.DispatchStateRepository;
import com.enruta.dispatch.repository.DriverRepository;
import com.enruta.dispatch.repository.OfferRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Vista de diagnostico del despacho: fase, ronda y a quien se le oferto. */
@RestController
@RequestMapping("/api/v1/dispatch")
public class DispatchQueryController {

    private final DispatchStateRepository states;
    private final OfferRepository offers;
    private final DriverRepository drivers;

    public DispatchQueryController(
            DispatchStateRepository states, OfferRepository offers, DriverRepository drivers) {
        this.states = states;
        this.offers = offers;
        this.drivers = drivers;
    }

    public record PendingOffer(
            UUID offerId,
            UUID driverId,
            String driverName,
            Instant expiresAt,
            int distanceMeters) {}

    public record DispatchStateView(
            UUID shipmentId,
            String phase,
            short round,
            UUID currentOfferId,
            Instant updatedAt,
            List<PendingOffer> pendingOffers) {}

    @GetMapping("/shipments/{shipmentId}")
    public DispatchStateView state(@PathVariable UUID shipmentId) {
        DispatchState state =
                states.findById(shipmentId)
                        .orElseThrow(
                                () ->
                                        BusinessException.notFound(
                                                "DISPATCH_NOT_FOUND", "Envio sin despacho"));

        List<PendingOffer> pending =
                offers.findByShipmentIdAndStatus(shipmentId, OfferStatus.CREATED).stream()
                        .map(
                                offer ->
                                        new PendingOffer(
                                                offer.getId(),
                                                offer.getDriverId(),
                                                drivers.findById(offer.getDriverId())
                                                        .map(Driver::getFullName)
                                                        .orElse("Conductor"),
                                                offer.getExpiresAt(),
                                                offer.getDistanceMeters() == null
                                                        ? 0
                                                        : offer.getDistanceMeters()))
                        .toList();

        return new DispatchStateView(
                state.getShipmentId(),
                state.getPhase().name(),
                state.getRound(),
                state.getCurrentOfferId(),
                state.getUpdatedAt(),
                pending);
    }
}
