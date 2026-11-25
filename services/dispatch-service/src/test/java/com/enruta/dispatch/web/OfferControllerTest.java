package com.enruta.dispatch.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.dispatch.service.DispatchService;
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
class OfferControllerTest {

    @Mock private DispatchService dispatchService;
    @Mock private Jwt jwt;

    private OfferController controller;
    private UUID driverId;

    @BeforeEach
    void setUp() {
        controller = new OfferController(dispatchService);
        driverId = UUID.randomUUID();
    }

    @Test
    void acceptReturnsTheCreatedAssignment() {
        UUID offerId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(driverId.toString());
        when(jwt.getClaimAsStringList("roles")).thenReturn(List.of("DRIVER"));
        when(dispatchService.accept(offerId, driverId, List.of("DRIVER")))
                .thenReturn(new DispatchService.AcceptResult(assignmentId, shipmentId, driverId));

        ResponseEntity<OfferController.AcceptResponse> response = controller.accept(offerId, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().assignmentId()).isEqualTo(assignmentId);
        assertThat(response.getBody().shipmentId()).isEqualTo(shipmentId);
        assertThat(response.getBody().driverId()).isEqualTo(driverId);
    }

    @Test
    void rejectReturnsNoContent() {
        UUID offerId = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(driverId.toString());
        when(jwt.getClaimAsStringList("roles")).thenReturn(List.of("DRIVER"));

        ResponseEntity<Void> response = controller.reject(offerId, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(dispatchService).reject(offerId, driverId, List.of("DRIVER"));
    }

    @Test
    void cancelAssignmentReturnsNoContent() {
        UUID assignmentId = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(driverId.toString());
        when(jwt.getClaimAsStringList("roles")).thenReturn(List.of("ADMIN"));

        ResponseEntity<Void> response = controller.cancelAssignment(assignmentId, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(dispatchService).cancelAssignment(assignmentId, driverId, List.of("ADMIN"));
    }

    @Test
    void missingRolesClaimBecomesAnEmptyList() {
        UUID offerId = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(driverId.toString());
        when(jwt.getClaimAsStringList("roles")).thenReturn(null);

        controller.reject(offerId, jwt);

        verify(dispatchService).reject(offerId, driverId, List.of());
    }
}
