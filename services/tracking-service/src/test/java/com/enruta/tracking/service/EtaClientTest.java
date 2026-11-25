package com.enruta.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Levanta un servidor HTTP de loopback (JDK) para probar el cliente sin salir a Internet. */
class EtaClientTest {

    private HttpServer server;
    private EtaClient client;
    private final AtomicReference<String> authorizationHeader = new AtomicReference<>();
    private volatile int status;
    private volatile String responseBody;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/eta", this::respond);
        server.start();
        client =
                new EtaClient(
                        "http://127.0.0.1:" + server.getAddress().getPort(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void respond(HttpExchange exchange) throws IOException {
        authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Test
    void theDurationIsParsedAndTheUserTokenIsForwarded() {
        status = 200;
        responseBody = "{\"durationSeconds\":120}";

        Long eta = client.etaSeconds(4.34, -74.36, 4.33, -74.35, "Bearer token-1");

        assertThat(eta).isEqualTo(120L);
        assertThat(authorizationHeader.get()).isEqualTo("Bearer token-1");
    }

    @Test
    void aResponseWithoutDurationReturnsNull() {
        status = 200;
        responseBody = "{\"route\":\"otra\"}";

        assertThat(client.etaSeconds(4.34, -74.36, 4.33, -74.35, "Bearer x")).isNull();
    }

    @Test
    void aServerErrorDegradesToNull() {
        status = 500;
        responseBody = "{\"error\":\"boom\"}";

        assertThat(client.etaSeconds(4.34, -74.36, 4.33, -74.35, "Bearer x")).isNull();
    }

    @Test
    void anUnparseableBodyDegradesToNull() {
        status = 200;
        responseBody = "esto no es json";

        assertThat(client.etaSeconds(4.34, -74.36, 4.33, -74.35, "Bearer x")).isNull();
    }
}
