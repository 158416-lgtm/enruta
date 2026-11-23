package com.enruta.shipment.web;

import com.enruta.common.error.BusinessException;
import com.enruta.common.idempotency.IdempotencyRecord;
import com.enruta.shipment.service.IdempotencyService;
import com.enruta.shipment.service.ShipmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipments")
public class ShipmentController {

    private final ShipmentService shipments;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public ShipmentController(
            ShipmentService shipments, IdempotencyService idempotency, ObjectMapper objectMapper) {
        this.shipments = shipments;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<ShipmentView> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateShipmentRequest request,
            @AuthenticationPrincipal Jwt jwt)
            throws Exception {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw BusinessException.badRequest(
                    "MISSING_IDEMPOTENCY_KEY", "El header Idempotency-Key es obligatorio");
        }
        UUID customerId = UUID.fromString(jwt.getSubject());
        String requestHash = sha256(objectMapper.writeValueAsString(request));

        if (!idempotency.claim(idempotencyKey, requestHash)) {
            IdempotencyRecord existing =
                    idempotency
                            .find(idempotencyKey)
                            .orElseThrow(
                                    () ->
                                            BusinessException.conflict(
                                                    "IDEMPOTENCY_IN_PROGRESS",
                                                    "Reintenta en un momento"));
            if (!existing.getRequestHash().equals(requestHash)) {
                throw BusinessException.conflict(
                        "IDEMPOTENCY_KEY_REUSED", "La clave ya se uso con un cuerpo distinto");
            }
            if (idempotency.isInProgress(existing)) {
                throw BusinessException.conflict(
                        "IDEMPOTENCY_IN_PROGRESS", "La peticion original aun se esta procesando");
            }
            ShipmentView replayed =
                    objectMapper.readValue(existing.getResponseBody(), ShipmentView.class);
            return ResponseEntity.status(existing.getResponseStatus()).body(replayed);
        }

        try {
            ShipmentView created = shipments.create(customerId, request);
            idempotency.complete(
                    idempotencyKey,
                    HttpStatus.CREATED.value(),
                    objectMapper.writeValueAsString(created));
            return ResponseEntity.created(URI.create("/api/v1/shipments/" + created.id()))
                    .body(created);
        } catch (RuntimeException ex) {
            idempotency.release(idempotencyKey);
            throw ex;
        }
    }

    @GetMapping("/{id}")
    public ShipmentView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return shipments.get(id, UUID.fromString(jwt.getSubject()), roles(jwt));
    }

    @PostMapping("/{id}/cancel")
    public ShipmentView cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return shipments.cancel(id, UUID.fromString(jwt.getSubject()), roles(jwt));
    }

    static List<String> roles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
