package com.enruta.dispatch.service;

import com.enruta.common.error.BusinessException;
import com.enruta.dispatch.domain.Driver;
import com.enruta.dispatch.domain.DriverStatus;
import com.enruta.dispatch.domain.Offer;
import com.enruta.dispatch.domain.OfferStatus;
import com.enruta.dispatch.repository.DriverRepository;
import com.enruta.dispatch.repository.OfferRepository;
import com.enruta.dispatch.web.OfferView;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverService {

    private final DriverRepository drivers;
    private final OfferRepository offers;
    private final CandidateFinder finder;

    public DriverService(DriverRepository drivers, OfferRepository offers, CandidateFinder finder) {
        this.drivers = drivers;
        this.offers = offers;
        this.finder = finder;
    }

    @Transactional
    public void updateLocation(
            UUID driverId, double lat, double lng, UUID callerId, List<String> roles) {
        Driver driver = load(driverId, callerId, roles);
        driver.moveTo(lat, lng);
        if (driver.getStatus() == DriverStatus.AVAILABLE) {
            finder.index(driverId, lat, lng);
        }
    }

    @Transactional
    public void setAvailability(
            UUID driverId, DriverStatus status, UUID callerId, List<String> roles) {
        Driver driver = load(driverId, callerId, roles);
        switch (status) {
            case AVAILABLE -> {
                driver.markAvailable();
                if (driver.getCurrentLat() != null && driver.getCurrentLng() != null) {
                    finder.index(driverId, driver.getCurrentLat(), driver.getCurrentLng());
                }
            }
            case BUSY -> {
                driver.markBusy();
                finder.unindex(driverId);
            }
            case OFFLINE -> {
                driver.markOffline();
                finder.unindex(driverId);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<OfferView> pendingOffers(UUID driverId, UUID callerId, List<String> roles) {
        load(driverId, callerId, roles);
        return offers.findByDriverIdAndStatus(driverId, OfferStatus.CREATED).stream()
                .map(DriverService::toView)
                .toList();
    }

    private Driver load(UUID driverId, UUID callerId, List<String> roles) {
        if (!roles.contains("ADMIN") && !driverId.equals(callerId)) {
            throw new BusinessException(
                    "FORBIDDEN", "Solo puedes reportar tu propia posicion", HttpStatus.FORBIDDEN);
        }
        return drivers.findById(driverId)
                .orElseThrow(
                        () ->
                                BusinessException.notFound(
                                        "DRIVER_NOT_FOUND", "Conductor inexistente"));
    }

    private static OfferView toView(Offer offer) {
        int eta = (int) Math.max(60, offer.getDistanceMeters() / (25_000.0 / 3600.0));
        return new OfferView(
                offer.getId(),
                offer.getShipmentId(),
                offer.getDriverId(),
                offer.getStatus(),
                offer.getExpiresAt(),
                offer.getRound(),
                offer.getDistanceMeters(),
                eta);
    }
}
