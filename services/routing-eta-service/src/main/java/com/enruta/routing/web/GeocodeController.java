package com.enruta.routing.web;

import com.enruta.routing.service.NominatimClient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Geocodificacion de direcciones con Nominatim (OpenStreetMap).
 *
 * <p>Es la segunda API externa del proyecto. La respuesta se guarda 24 h en Redis porque Nominatim
 * pide no repetir consultas y limita a una peticion por segundo.
 */
@RestController
@RequestMapping("/api/v1/eta")
public class GeocodeController {

    private static final Logger log = LoggerFactory.getLogger(GeocodeController.class);
    private static final Duration TTL = Duration.ofHours(24);

    private final NominatimClient nominatim;
    private final StringRedisTemplate redis;

    public GeocodeController(NominatimClient nominatim, StringRedisTemplate redis) {
        this.nominatim = nominatim;
        this.redis = redis;
    }

    public record GeocodeResponse(
            String query,
            Double lat,
            Double lng,
            String displayName,
            boolean cached,
            String source) {}

    @GetMapping("/geocode")
    public GeocodeResponse geocode(@RequestParam @NotBlank @Size(max = 200) String q) {
        String key = "geocode:" + q.trim().toLowerCase();
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            String[] parts = cached.split("\\|", 3);
            if (parts.length == 3) {
                return new GeocodeResponse(
                        q,
                        Double.valueOf(parts[0]),
                        Double.valueOf(parts[1]),
                        parts[2],
                        true,
                        "CACHE");
            }
            log.debug("entrada de geocodificacion ilegible, se recalcula");
        }

        NominatimClient.Place place = nominatim.search(q);
        if (place == null) {
            return new GeocodeResponse(q, null, null, null, false, "NOMINATIM");
        }

        try {
            redis.opsForValue()
                    .set(key, place.lat() + "|" + place.lng() + "|" + place.displayName(), TTL);
        } catch (RuntimeException ex) {
            log.debug("no se pudo cachear la geocodificacion: {}", ex.getMessage());
        }
        return new GeocodeResponse(
                q, place.lat(), place.lng(), place.displayName(), false, "NOMINATIM");
    }
}
