package com.enruta.dispatch.web;

import com.enruta.dispatch.service.DispatchService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OfferController {

    private final DispatchService dispatchService;

    public OfferController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostMapping("/offers/{id}/accept")
    public ResponseEntity<AcceptResponse> accept(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        DispatchService.AcceptResult result =
                dispatchService.accept(
                        id, DriverController.subject(jwt), DriverController.roles(jwt));
        return ResponseEntity.ok(
                new AcceptResponse(result.assignmentId(), result.shipmentId(), result.driverId()));
    }

    @PostMapping("/offers/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        dispatchService.reject(id, DriverController.subject(jwt), DriverController.roles(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/assignments/{id}/cancel")
    public ResponseEntity<Void> cancelAssignment(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        dispatchService.cancelAssignment(
                id, DriverController.subject(jwt), DriverController.roles(jwt));
        return ResponseEntity.noContent().build();
    }

    public record AcceptResponse(UUID assignmentId, UUID shipmentId, UUID driverId) {}
}
