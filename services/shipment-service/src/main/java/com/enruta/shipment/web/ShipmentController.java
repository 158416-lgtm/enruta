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
import org.springframework.web.bind.annotation.RequestParam;
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
            ShipmentView created =
                    shipments.create(customerId, jwt.getClaimAsString("email"), request);
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

    @GetMapping
    public org.springframework.data.domain.Page<ShipmentView> list(
            @RequestParam(required = false) com.enruta.shipment.domain.ShipmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        var pageable =
                org.springframework.data.domain.PageRequest.of(
                        page,
                        Math.min(size, 50),
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        return shipments.list(UUID.fromString(jwt.getSubject()), roles(jwt), status, pageable);
    }

    @GetMapping("/{id}")
    public ShipmentView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return shipments.get(id, UUID.fromString(jwt.getSubject()), roles(jwt));
    }

    @PostMapping("/{id}/cancel")
    public ShipmentView cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return shipments.cancel(id, UUID.fromString(jwt.getSubject()), roles(jwt));
    }

    /** Timeline completo del envio: quien cambio el estado y por que. */
    @GetMapping("/{id}/history")
    public List<HistoryView> history(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return shipments.history(id, UUID.fromString(jwt.getSubject()), roles(jwt)).stream()
                .map(HistoryView::from)
                .toList();
    }

    /** Asignacion manual de un conductor (soporte cuando el despacho automatico falla). */
    @PostMapping("/{id}/assign")
    public ShipmentView assign(
            @PathVariable UUID id, @RequestParam UUID driverId, @AuthenticationPrincipal Jwt jwt) {
        if (!roles(jwt).contains("ADMIN")) {
            throw new BusinessException(
                    "FORBIDDEN", "Solo un administrador asigna manualmente", HttpStatus.FORBIDDEN);
        }
        return shipments.assign(id, driverId, UUID.fromString(jwt.getSubject()));
    }

    /** Alternativa a la geocerca cuando el GPS del repartidor no funciona. */
    @PostMapping("/{id}/confirm-pickup")
    public ShipmentView confirmPickup(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return shipments.confirmPickup(id, UUID.fromString(jwt.getSubject()), roles(jwt));
    }

    @PostMapping("/{id}/confirm-delivery")
    public ShipmentView confirmDelivery(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return shipments.confirmDelivery(id, UUID.fromString(jwt.getSubject()), roles(jwt));
    }

    /** Conteo por estado para el tablero de operacion. */
    @GetMapping("/summary")
    public java.util.Map<String, Long> summary(@AuthenticationPrincipal Jwt jwt) {
        return shipments.summary(UUID.fromString(jwt.getSubject()), roles(jwt));
    }

    public record HistoryView(
            Long id,
            String fromStatus,
            String toStatus,
            UUID actorId,
            String reason,
            java.time.Instant occurredAt) {

        public static HistoryView from(com.enruta.shipment.domain.ShipmentStatusHistory entry) {
            return new HistoryView(
                    entry.getId(),
                    entry.getFromStatus() == null ? null : entry.getFromStatus().name(),
                    entry.getToStatus().name(),
                    entry.getActorId(),
                    entry.getReason(),
                    entry.getOccurredAt());
        }
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
