package com.enruta.tracking.service;

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
 * Cliente del servicio de ETA.
 *
 * <p>Llamada sincrona y acotada: 2 s de timeout y {@code null} si falla. El mapa se dibuja igual
 * sin ETA; nunca se bloquea la consulta de estado por un servicio auxiliar. Se propaga el token del
 * usuario para no abrir un endpoint interno sin autenticar.
 */
@Component
public class EtaClient {

    private static final Logger log = LoggerFactory.getLogger(EtaClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public EtaClient(
            @Value("${enruta.tracking.routing-url}") String baseUrl, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(1).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(2).toMillis());
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.objectMapper = objectMapper;
    }

    /**
     * @return segundos estimados, o {@code null} si el servicio de rutas no responde.
     */
    public Long etaSeconds(
            double fromLat, double fromLng, double toLat, double toLng, String authorization) {
        try {
            String body =
                    restClient
                            .get()
                            .uri(
                                    "/api/v1/eta?fromLat={fromLat}&fromLng={fromLng}&toLat={toLat}&toLng={toLng}",
                                    fromLat,
                                    fromLng,
                                    toLat,
                                    toLng)
                            .header("Authorization", authorization)
                            .retrieve()
                            .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            return root.hasNonNull("durationSeconds") ? root.get("durationSeconds").asLong() : null;
        } catch (Exception ex) {
            log.debug("ETA no disponible: {}", ex.getMessage());
            return null;
        }
    }
}
