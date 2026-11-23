package com.enruta.dispatch.web;

import com.enruta.dispatch.service.DriverService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping("/{id}/location")
    public ResponseEntity<Void> updateLocation(
            @PathVariable UUID id,
            @Valid @RequestBody LocationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        driverService.updateLocation(id, request.lat(), request.lng(), subject(jwt), roles(jwt));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/availability")
    public ResponseEntity<Void> setAvailability(
            @PathVariable UUID id,
            @Valid @RequestBody AvailabilityRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        driverService.setAvailability(id, request.status(), subject(jwt), roles(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/offers")
    public List<OfferView> offers(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return driverService.pendingOffers(id, subject(jwt), roles(jwt));
    }

    static UUID subject(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    static List<String> roles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }
}
