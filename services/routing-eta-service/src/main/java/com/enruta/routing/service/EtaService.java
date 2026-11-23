package com.enruta.routing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * ETA con cache explicita.
 *
 * <p>La clave redondea a 4 decimales (~11 m): dos peticiones del mismo tramo comparten entrada sin
 * perder precision util. Se hace a mano en lugar de {@code @Cacheable} para poder devolver el flag
 * {@code cached} y medir aciertos/fallos.
 */
@Service
public class EtaService {

    private static final Logger log = LoggerFactory.getLogger(EtaService.class);

    private final OsrmClient osrm;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final Counter hits;
    private final Counter misses;
    private final Counter fallbacks;

    public EtaService(
            OsrmClient osrm,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            MeterRegistry meters,
            @Value("${enruta.routing.cache-ttl:20s}") Duration ttl) {
        this.osrm = osrm;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
        this.hits = Counter.builder("eta_requests_total").tag("result", "hit").register(meters);
        this.misses = Counter.builder("eta_requests_total").tag("result", "miss").register(meters);
        this.fallbacks =
                Counter.builder("eta_requests_total").tag("result", "fallback").register(meters);
    }

    public RouteResult route(double fromLat, double fromLng, double toLat, double toLng) {
        String key = cacheKey(fromLat, fromLng, toLat, toLng);
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            hits.increment();
            try {
                return objectMapper.readValue(cached, RouteResult.class).asCached();
            } catch (Exception ex) {
                log.debug("entrada de cache ilegible, se recalcula: {}", ex.getMessage());
            }
        }

        misses.increment();
        RouteResult result = osrm.route(fromLat, fromLng, toLat, toLng);
        if ("FALLBACK".equals(result.source())) {
            fallbacks.increment();
        }
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(result), ttl);
        } catch (Exception ex) {
            log.debug("no se pudo cachear el ETA: {}", ex.getMessage());
        }
        return result;
    }

    public static String cacheKey(double fromLat, double fromLng, double toLat, double toLng) {
        // ~11 m de precision: suficiente para compartir cache sin mezclar calles distintas.
        return String.format(
                Locale.ROOT, "eta:%.4f,%.4f:%.4f,%.4f", fromLat, fromLng, toLat, toLng);
    }
}
