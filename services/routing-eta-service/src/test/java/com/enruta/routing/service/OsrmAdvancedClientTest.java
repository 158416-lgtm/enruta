package com.enruta.routing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OsrmAdvancedClientTest {

    private static HttpServer osrm;
    private static String baseUrl;

    @BeforeAll
    static void start() throws Exception {
        osrm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        osrm.createContext(
                "/route/v1/driving/",
                exchange -> {
                    String body =
                            exchange.getRequestURI().getQuery().contains("overview=false")
                                    ? "{\"routes\":[{\"distance\":1200.5,\"duration\":90.2}]}"
                                    : "{\"routes\":[{\"distance\":1200.5,\"duration\":90.2,"
                                            + "\"geometry\":{\"coordinates\":[[-74.36,4.34],[-74.35,4.35]]}}]}";
                    respond(exchange, body);
                });
        osrm.createContext(
                "/table/v1/driving/",
                exchange -> respond(exchange, "{\"distances\":[[0,1500.2],[1800.4,0]]}"));
        osrm.createContext(
                "/nearest/v1/driving/",
                exchange -> respond(exchange, "{\"waypoints\":[{\"location\":[-74.36,4.34]}]}"));
        osrm.start();
        baseUrl = "http://127.0.0.1:" + osrm.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        osrm.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private OsrmAdvancedClient client(String url) {
        return new OsrmAdvancedClient(new ObjectMapper(), url, Duration.ofSeconds(2), 25);
    }

    @Test
    void routeReadsDistanceDurationAndGeometry() {
        OsrmAdvancedClient.Route route = client(baseUrl).route(4.34, -74.36, 4.35, -74.35);

        assertThat(route.distanceMeters()).isEqualTo(1200.5);
        assertThat(route.durationSeconds()).isEqualTo(90.2);
        assertThat(route.geometry())
                .containsExactly(new double[] {4.34, -74.36}, new double[] {4.35, -74.35});
    }

    @Test
    void distanceReadsOnlyMeters() {
        assertThat(client(baseUrl).distance(4.34, -74.36, 4.35, -74.35)).isEqualTo(1200.5);
    }

    @Test
    void matrixBuildsAnNByMMatrix() {
        double[][] matrix =
                client(baseUrl)
                        .matrix(
                                List.of(new double[] {4.34, -74.36}),
                                List.of(new double[] {4.35, -74.35}, new double[] {4.36, -74.34}));

        assertThat(matrix).hasDimensions(1, 2);
        assertThat(matrix[0][0]).isZero();
        assertThat(matrix[0][1]).isEqualTo(1500.2);
    }

    @Test
    void nearestReturnsTheSnappedPoint() {
        assertThat(client(baseUrl).nearest(4.341, -74.361)).containsExactly(4.34, -74.36);
    }

    @Test
    void unreadableResponseRaisesAnIllegalState() throws Exception {
        HttpServer broken = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        broken.createContext(
                "/route/v1/driving/", exchange -> respond(exchange, "esto no es json"));
        broken.start();
        try {
            String url = "http://127.0.0.1:" + broken.getAddress().getPort();
            assertThatThrownBy(() -> client(url).route(4.34, -74.36, 4.35, -74.35))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ilegible");
        } finally {
            broken.stop(0);
        }
    }

    @Test
    void fallbacksUseStraightLineWhenOsrmIsDown() {
        OsrmAdvancedClient down = client("http://127.0.0.1:1");
        OsrmAdvancedClient.Route route =
                down.routeFallback(4.34, -74.36, 4.35, -74.35, new RuntimeException("caido"));
        double distance =
                down.distanceFallback(4.34, -74.36, 4.35, -74.35, new RuntimeException("caido"));
        double[][] matrix =
                down.matrixFallback(
                        List.of(new double[] {4.34, -74.36}),
                        List.of(new double[] {4.35, -74.35}),
                        new RuntimeException("caido"));
        double[] nearest = down.nearestFallback(4.34, -74.36, new RuntimeException("caido"));

        assertThat(route.distanceMeters()).isPositive();
        assertThat(route.geometry()).hasSize(2);
        assertThat(distance).isPositive();
        assertThat(matrix[0][0]).isPositive();
        assertThat(nearest).containsExactly(4.34, -74.36);
    }
}
