package com.enruta.routing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente de Nominatim (OpenStreetMap) para convertir una dirección en coordenadas.
 *
 * <p>Es la segunda API externa del proyecto (la primera es OSRM). Nominatim exige un User-Agent
 * identificable y limita a una petición por segundo, por eso se cachea la respuesta.
 */
@Component
public class NominatimClient {

    private static final Logger log = LoggerFactory.getLogger(NominatimClient.class);

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public NominatimClient(
            ObjectMapper objectMapper,
            @Value("${enruta.routing.nominatim-url:https://nominatim.openstreetmap.org}")
                    String baseUrl,
            @Value("${enruta.routing.nominatim-timeout:3s}") Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        this.client =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .requestFactory(factory)
                        .defaultHeader("User-Agent", "enruta-demo/1.0 (proyecto universitario)")
                        .build();
        this.objectMapper = objectMapper;
    }

    public record Place(double lat, double lng, String displayName) {}

    /**
     * @return el primer resultado, o {@code null} si no hay coincidencias o falla la red.
     */
    public Place search(String query) {
        try {
            String body =
                    client.get()
                            .uri("/search?q={q}&format=jsonv2&limit=1&countrycodes=co", query)
                            .retrieve()
                            .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray() || root.isEmpty()) {
                return null;
            }
            JsonNode first = root.get(0);
            return new Place(
                    first.path("lat").asDouble(),
                    first.path("lon").asDouble(),
                    first.path("display_name").asText(query));
        } catch (Exception ex) {
            log.warn("Nominatim no disponible: {}", ex.getMessage());
            return null;
        }
    }
}
