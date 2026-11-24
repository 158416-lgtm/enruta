package com.enruta.tracking.web;

import com.enruta.common.error.BusinessException;
import com.enruta.tracking.messaging.GpsProducer;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingesta GPS. Responde 202 siempre: el cliente no espera a Kafka (ADR-0011).
 *
 * <p>El rate limit por conductor evita que un GPS defectuoso inunde el pipeline.
 */
@RestController
@RequestMapping("/api/v1/tracking")
public class LocationController {

    private final int maxBatchesPerSecond;

    private final GpsProducer producer;
    private final StringRedisTemplate redis;

    public LocationController(
            GpsProducer producer,
            StringRedisTemplate redis,
            @org.springframework.beans.factory.annotation.Value(
                            "${enruta.tracking.ingest.max-batches-per-second:3}")
                    int maxBatchesPerSecond) {
        this.producer = producer;
        this.redis = redis;
        this.maxBatchesPerSecond = maxBatchesPerSecond;
    }

    @PostMapping("/drivers/{id}/location")
    public ResponseEntity<Void> ingest(
            @PathVariable UUID id,
            @Valid @RequestBody LocationBatchRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID caller = UUID.fromString(jwt.getSubject());
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (!id.equals(caller) && (roles == null || !roles.contains("ADMIN"))) {
            throw new BusinessException(
                    "FORBIDDEN", "Solo puedes reportar tu propia posicion", HttpStatus.FORBIDDEN);
        }

        String rateKey = "rl:gps:" + id;
        Long count = redis.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            redis.expire(rateKey, java.time.Duration.ofSeconds(1));
        }
        if (count != null && count > maxBatchesPerSecond) {
            throw new BusinessException(
                    "RATE_LIMITED",
                    "Demasiados reportes por segundo",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        producer.publish(id, request.points());
        return ResponseEntity.accepted().build();
    }
}
