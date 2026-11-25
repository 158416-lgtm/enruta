package com.enruta.routing.service;

import com.enruta.common.geo.GeoMath;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Operaciones avanzadas de OSRM: geometría de ruta, matriz de distancias y punto más cercano a una
 * calle. Todas tienen un plan B para no dejar al cliente sin respuesta.
 */
@Component
public class OsrmAdvancedClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmAdvancedClient.class);

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final double fallbackSpeedKph;

    public OsrmAdvancedClient(
            ObjectMapper objectMapper,
            @Value("${enruta.routing.osrm-base-url}") String baseUrl,
            @Value("${enruta.routing.osrm-timeout:2s}") Duration timeout,
            @Value("${enruta.routing.fallback-average-speed-kph:25}") double fallbackSpeedKph) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.fallbackSpeedKph = fallbackSpeedKph;
    }

    public record Route(double distanceMeters, double durationSeconds, List<double[]> geometry) {}

    @CircuitBreaker(name = "osrm", fallbackMethod = "routeFallback")
    public Route route(double fromLat, double fromLng, double toLat, double toLng) {
        String body =
                client.get()
                        .uri(
                                "/route/v1/driving/{fl},{fg};{tl},{tg}?overview=full&geometries=geojson",
                                fromLng,
                                fromLat,
                                toLng,
                                toLat)
                        .retrieve()
                        .body(String.class);
        JsonNode root = parse(body);
        JsonNode route = root.at("/routes/0");
        List<double[]> geometry = new ArrayList<>();
        route.at("/geometry/coordinates")
                .forEach(
                        node ->
                                geometry.add(
                                        new double[] {
                                            node.get(1).asDouble(), node.get(0).asDouble()
                                        }));
        return new Route(
                route.path("distance").asDouble(), route.path("duration").asDouble(), geometry);
    }

    @CircuitBreaker(name = "osrm", fallbackMethod = "distanceFallback")
    public double distance(double fromLat, double fromLng, double toLat, double toLng) {
        String body =
                client.get()
                        .uri(
                                "/route/v1/driving/{fl},{fg};{tl},{tg}?overview=false",
                                fromLng,
                                fromLat,
                                toLng,
                                toLat)
                        .retrieve()
                        .body(String.class);
        JsonNode root = parse(body);
        return root.at("/routes/0/distance").asDouble();
    }

    @CircuitBreaker(name = "osrm", fallbackMethod = "matrixFallback")
    public double[][] matrix(List<double[]> origins, List<double[]> destinations) {
        StringBuilder coordinates = new StringBuilder();
        for (double[] point : origins) {
            coordinates.append(point[1]).append(',').append(point[0]).append(';');
        }
        for (int i = 0; i < destinations.size(); i++) {
            coordinates.append(destinations.get(i)[1]).append(',').append(destinations.get(i)[0]);
            if (i < destinations.size() - 1) {
                coordinates.append(';');
            }
        }
        String body =
                client.get()
                        .uri(
                                "/table/v1/driving/{coords}?annotations=distance",
                                coordinates.toString())
                        .retrieve()
                        .body(String.class);
        JsonNode distances = parse(body).path("distances");
        double[][] result = new double[origins.size()][destinations.size()];
        for (int i = 0; i < origins.size(); i++) {
            for (int j = 0; j < destinations.size(); j++) {
                result[i][j] = distances.get(i).get(j).asDouble();
            }
        }
        return result;
    }

    @CircuitBreaker(name = "osrm", fallbackMethod = "nearestFallback")
    public double[] nearest(double lat, double lng) {
        String body =
                client.get()
                        .uri("/nearest/v1/driving/{lng},{lat}?number=1", lng, lat)
                        .retrieve()
                        .body(String.class);
        JsonNode location = parse(body).at("/waypoints/0/location");
        return new double[] {location.get(1).asDouble(), location.get(0).asDouble()};
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalStateException("respuesta de OSRM ilegible", ex);
        }
    }

    // --- planes B ---

    public Route routeFallback(
            double fromLat, double fromLng, double toLat, double toLng, Throwable cause) {
        log.warn("OSRM no disponible (ruta): {}", cause.toString());
        double straight = GeoMath.distanceMeters(fromLat, fromLng, toLat, toLng);
        return new Route(
                straight,
                GeoMath.estimatedDurationSeconds(straight, fallbackSpeedKph),
                List.of(new double[] {fromLat, fromLng}, new double[] {toLat, toLng}));
    }

    public double distanceFallback(
            double fromLat, double fromLng, double toLat, double toLng, Throwable cause) {
        log.warn("OSRM no disponible (distancia): {}", cause.toString());
        return GeoMath.distanceMeters(fromLat, fromLng, toLat, toLng);
    }

    public double[][] matrixFallback(
            List<double[]> origins, List<double[]> destinations, Throwable cause) {
        log.warn("OSRM no disponible (matriz): {}", cause.toString());
        double[][] result = new double[origins.size()][destinations.size()];
        for (int i = 0; i < origins.size(); i++) {
            for (int j = 0; j < destinations.size(); j++) {
                result[i][j] =
                        GeoMath.distanceMeters(
                                origins.get(i)[0],
                                origins.get(i)[1],
                                destinations.get(j)[0],
                                destinations.get(j)[1]);
            }
        }
        return result;
    }

    public double[] nearestFallback(double lat, double lng, Throwable cause) {
        log.warn("OSRM no disponible (nearest): {}", cause.toString());
        return new double[] {lat, lng};
    }
}
