package com.enruta.dispatch.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.dispatch.domain.DriverStatus;
import com.enruta.dispatch.domain.OfferStatus;
import com.enruta.dispatch.service.DriverService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class DriverControllerTest {

    @Mock private DriverService driverService;
    @Mock private Jwt jwt;

    private DriverController controller;
    private UUID driverId;

    @BeforeEach
    void setUp() {
        controller = new DriverController(driverService);
        driverId = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(driverId.toString());
        when(jwt.getClaimAsStringList("roles")).thenReturn(List.of("DRIVER"));
    }

    @Test
    void updateLocationIsAccepted() {
        ResponseEntity<Void> response =
                controller.updateLocation(driverId, new LocationRequest(4.35, -74.36), jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(driverService).updateLocation(driverId, 4.35, -74.36, driverId, List.of("DRIVER"));
    }

    @Test
    void settingAvailabilityReturnsNoContent() {
        ResponseEntity<Void> response =
                controller.setAvailability(
                        driverId, new AvailabilityRequest(DriverStatus.OFFLINE), jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(driverService)
                .setAvailability(driverId, DriverStatus.OFFLINE, driverId, List.of("DRIVER"));
    }

    @Test
    void pendingOffersAreReturned() {
        OfferView offer =
                new OfferView(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        driverId,
                        OfferStatus.CREATED,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        (short) 1,
                        500,
                        72);
        when(driverService.pendingOffers(driverId, driverId, List.of("DRIVER")))
                .thenReturn(List.of(offer));

        List<OfferView> offers = controller.offers(driverId, jwt);

        assertThat(offers).containsExactly(offer);
    }
}
