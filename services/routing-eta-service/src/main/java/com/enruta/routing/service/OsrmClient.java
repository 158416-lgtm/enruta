package com.enruta.routing.service;

import com.enruta.common.geo.GeoMath;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente de OSRM con cortocircuito.
 *
 * <p>Si OSRM tarda o falla, el circuito se abre y las siguientes llamadas van directo al fallback
 * en lugar de esperar 2 s cada una. Eso protege a quien consulta el ETA.
 */
@Component
public class OsrmClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final double fallbackSpeedKph;

    public OsrmClient(
            ObjectMapper objectMapper,
            @Value("${enruta.routing.osrm-base-url}") String baseUrl,
            @Value("${enruta.routing.osrm-timeout:2s}") Duration timeout,
            @Value("${enruta.routing.fallback-average-speed-kph:25}") double fallbackSpeedKph) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.fallbackSpeedKph = fallbackSpeedKph;
    }

    @CircuitBreaker(name = "osrm", fallbackMethod = "fallback")
    public RouteResult route(double fromLat, double fromLng, double toLat, double toLng) {
        String body =
                restClient
                        .get()
                        .uri(
                                "/route/v1/driving/{fromLng},{fromLat};{toLng},{toLat}?overview=false",
                                fromLng,
                                fromLat,
                                toLng,
                                toLat)
                        .retrieve()
                        .body(String.class);

        JsonNode root = parse(body);
        if (!"Ok".equals(root.path("code").asText())) {
            throw new IllegalStateException("OSRM respondio " + root.path("code").asText());
        }
        JsonNode route = root.at("/routes/0");
        return new RouteResult(
                Math.round(route.path("distance").asDouble()),
                Math.round(route.path("duration").asDouble()),
                false,
                "OSRM");
    }

    /** Plan B: linea recta con factor de rodeo. Nunca falla, nunca bloquea. */
    public RouteResult fallback(
            double fromLat, double fromLng, double toLat, double toLng, Throwable cause) {
        double straight = GeoMath.distanceMeters(fromLat, fromLng, toLat, toLng);
        log.warn("usando fallback para ETA: {}", cause.toString());
        return new RouteResult(
                Math.round(straight),
                GeoMath.estimatedDurationSeconds(straight, fallbackSpeedKph),
                false,
                "FALLBACK");
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException("respuesta de OSRM ilegible", ex);
        }
    }
}
