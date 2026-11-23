package com.enruta.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** Cliente HTTP del simulador: login, rutas de OSRM, posiciones, envios y ofertas. */
@Component
public class SimulatorClient {

    private static final Logger log = LoggerFactory.getLogger(SimulatorClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public record ShipmentGeometry(
            double pickupLat, double pickupLng, double dropoffLat, double dropoffLng) {}

    private final WebClient http = WebClient.builder().build();
    private final ObjectMapper objectMapper;
    private final SimulatorProperties properties;

    public SimulatorClient(ObjectMapper objectMapper, SimulatorProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String login(String email) {
        String body =
                http.post()
                        .uri(properties.authUrl() + "/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("email", email, "password", properties.password()))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(TIMEOUT);
        try {
            return objectMapper.readTree(body).get("accessToken").asText();
        } catch (Exception ex) {
            throw new IllegalStateException("login fallido para " + email, ex);
        }
    }

    /**
     * Ruta real por calles entre dos puntos. Si OSRM no responde se usa una interpolacion lineal
     * para que la demo nunca se quede sin movimiento.
     */
    public List<double[]> route(double lat1, double lng1, double lat2, double lng2) {
        try {
            String body =
                    http.get()
                            .uri(
                                    properties.osrmUrl()
                                            + "/route/v1/driving/"
                                            + lng1
                                            + ","
                                            + lat1
                                            + ";"
                                            + lng2
                                            + ","
                                            + lat2
                                            + "?overview=full&geometries=geojson")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(TIMEOUT);
            JsonNode coordinates = objectMapper.readTree(body).at("/routes/0/geometry/coordinates");
            List<double[]> points = new ArrayList<>();
            coordinates.forEach(
                    node ->
                            points.add(
                                    new double[] {node.get(1).asDouble(), node.get(0).asDouble()}));
            if (points.size() >= 2) {
                return points;
            }
        } catch (Exception ex) {
            log.warn("OSRM no disponible, ruta sintetica: {}", ex.getMessage());
        }
        return straightLine(lat1, lng1, lat2, lng2);
    }

    /** Tracking es ahora el unico dueno del GPS (ADR-0011). */
    public void postLocation(String token, UUID driverId, double lat, double lng) {
        try {
            http.post()
                    .uri(
                            properties.trackingUrl()
                                    + "/api/v1/tracking/drivers/"
                                    + driverId
                                    + "/location")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("points", List.of(Map.of("lat", lat, "lng", lng))))
                    .retrieve()
                    .toBodilessEntity()
                    .block(TIMEOUT);
        } catch (Exception ex) {
            log.debug("no se pudo reportar posicion driver={}: {}", driverId, ex.getMessage());
        }
    }

    public List<JsonNode> pendingOffers(String token, UUID driverId) {
        try {
            String body =
                    http.get()
                            .uri(
                                    properties.dispatchUrl()
                                            + "/api/v1/drivers/"
                                            + driverId
                                            + "/offers")
                            .header("Authorization", "Bearer " + token)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(TIMEOUT);
            List<JsonNode> offers = new ArrayList<>();
            objectMapper.readTree(body).forEach(offers::add);
            return offers;
        } catch (Exception ex) {
            return List.of();
        }
    }

    public boolean accept(String token, UUID offerId) {
        try {
            http.post()
                    .uri(properties.dispatchUrl() + "/api/v1/offers/" + offerId + "/accept")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity()
                    .block(TIMEOUT);
            return true;
        } catch (Exception ex) {
            log.debug("oferta {} ya no disponible: {}", offerId, ex.getMessage());
            return false;
        }
    }

    /** Geometria del envio asignado; null mientras la asignacion no se haya propagado. */
    public ShipmentGeometry shipment(String token, UUID shipmentId) {
        try {
            String body =
                    http.get()
                            .uri(properties.shipmentUrl() + "/api/v1/shipments/" + shipmentId)
                            .header("Authorization", "Bearer " + token)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(TIMEOUT);
            JsonNode root = objectMapper.readTree(body);
            return new ShipmentGeometry(
                    root.at("/pickup/lat").asDouble(),
                    root.at("/pickup/lng").asDouble(),
                    root.at("/dropoff/lat").asDouble(),
                    root.at("/dropoff/lng").asDouble());
        } catch (Exception ex) {
            return null;
        }
    }

    private static List<double[]> straightLine(double lat1, double lng1, double lat2, double lng2) {
        List<double[]> points = new ArrayList<>();
        int steps = 40;
        for (int i = 0; i <= steps; i++) {
            double ratio = i / (double) steps;
            points.add(new double[] {lat1 + (lat2 - lat1) * ratio, lng1 + (lng2 - lng1) * ratio});
        }
        return points;
    }
}
