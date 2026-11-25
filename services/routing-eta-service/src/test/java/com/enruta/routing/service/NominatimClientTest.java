package com.enruta.routing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NominatimClientTest {

    private static HttpServer nominatim;
    private static String baseUrl;

    @BeforeAll
    static void start() throws Exception {
        nominatim = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        nominatim.createContext(
                "/search",
                exchange -> {
                    String query = exchange.getRequestURI().getQuery();
                    String body =
                            query.contains("vacio")
                                    ? "[]"
                                    : "[{\"lat\":\"4.3449\",\"lon\":\"-74.3674\","
                                            + "\"display_name\":\"Fusagasuga, Cundinamarca\"}]";
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                });
        nominatim.start();
        baseUrl = "http://127.0.0.1:" + nominatim.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        nominatim.stop(0);
    }

    @Test
    void searchReturnsTheFirstMatch() {
        NominatimClient client =
                new NominatimClient(new ObjectMapper(), baseUrl, Duration.ofSeconds(2));

        NominatimClient.Place place = client.search("Fusagasuga");

        assertThat(place).isNotNull();
        assertThat(place.lat()).isEqualTo(4.3449);
        assertThat(place.lng()).isEqualTo(-74.3674);
        assertThat(place.displayName()).contains("Fusagasuga");
    }

    @Test
    void emptyResultReturnsNull() {
        NominatimClient client =
                new NominatimClient(new ObjectMapper(), baseUrl, Duration.ofSeconds(2));

        assertThat(client.search("vacio")).isNull();
    }

    @Test
    void unreachableServerReturnsNullInsteadOfFailing() {
        NominatimClient client =
                new NominatimClient(
                        new ObjectMapper(), "http://127.0.0.1:1", Duration.ofMillis(300));

        assertThat(client.search("Fusagasuga")).isNull();
    }
}
