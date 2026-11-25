package com.enruta.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.common.error.BusinessException;
import com.enruta.dispatch.domain.Driver;
import com.enruta.dispatch.domain.DriverStatus;
import com.enruta.dispatch.domain.Offer;
import com.enruta.dispatch.domain.OfferStatus;
import com.enruta.dispatch.repository.DriverRepository;
import com.enruta.dispatch.repository.OfferRepository;
import com.enruta.dispatch.web.OfferView;
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
class DriverServiceTest {

    @Mock private DriverRepository drivers;
    @Mock private OfferRepository offers;
    @Mock private CandidateFinder finder;

    private DriverService service;
    private UUID driverId;
    private Driver driver;

    @BeforeEach
    void setUp() {
        service = new DriverService(drivers, offers, finder);
        driverId = UUID.randomUUID();
        driver = new Driver(driverId, "Ana", "MOTO", 4.9, 4.3449, -74.3674);
        org.mockito.Mockito.lenient()
                .when(drivers.findById(driverId))
                .thenReturn(Optional.of(driver));
    }

    @Test
    void driverCanReportItsOwnPositionAndGetsIndexed() {
        service.updateLocation(driverId, 4.35, -74.36, driverId, List.of("DRIVER"));

        assertThat(driver.getCurrentLat()).isEqualTo(4.35);
        verify(finder).index(driverId, 4.35, -74.36);
    }

    @Test
    void anotherDriverCannotReportYourPosition() {
        assertThatThrownBy(
                        () ->
                                service.updateLocation(
                                        driverId,
                                        4.35,
                                        -74.36,
                                        UUID.randomUUID(),
                                        List.of("DRIVER")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("FORBIDDEN");
        verify(finder, never())
                .index(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyDouble(),
                        org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void adminCanReportAnyPosition() {
        service.updateLocation(driverId, 4.35, -74.36, UUID.randomUUID(), List.of("ADMIN"));

        assertThat(driver.getCurrentLat()).isEqualTo(4.35);
    }

    @Test
    void goingOfflineRemovesTheDriverFromTheMap() {
        service.setAvailability(driverId, DriverStatus.OFFLINE, driverId, List.of("DRIVER"));

        assertThat(driver.getStatus()).isEqualTo(DriverStatus.OFFLINE);
        verify(finder).unindex(driverId);
    }

    @Test
    void goingAvailableIndexesTheLastKnownPosition() {
        service.setAvailability(driverId, DriverStatus.AVAILABLE, driverId, List.of("DRIVER"));

        assertThat(driver.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        verify(finder).index(driverId, 4.3449, -74.3674);
    }

    @Test
    void pendingOffersAreExposedWithAnEta() {
        UUID shipmentId = UUID.randomUUID();
        Offer offer =
                new Offer(shipmentId, driverId, (short) 1, 900, 0.8, Instant.now().plusSeconds(60));
        when(offers.findByDriverIdAndStatus(driverId, OfferStatus.CREATED))
                .thenReturn(List.of(offer));

        List<OfferView> views = service.pendingOffers(driverId, driverId, List.of("DRIVER"));

        assertThat(views).hasSize(1);
        assertThat(views.getFirst().shipmentId()).isEqualTo(shipmentId);
        assertThat(views.getFirst().distanceMeters()).isEqualTo(900);
        assertThat(views.getFirst().etaSeconds()).isPositive();
    }

    @Test
    void unknownDriverIsNotFound() {
        UUID other = UUID.randomUUID();
        when(drivers.findById(other)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateLocation(other, 1, 1, other, List.of("DRIVER")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("DRIVER_NOT_FOUND");
    }
}
