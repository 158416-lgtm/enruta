package com.enruta.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba con un OSRM falso (HttpServer del JDK) y Redis real.
 *
 * <p>Cubre los tres caminos: respuesta de OSRM, cache y fallback cuando OSRM falla.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class EtaApiTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static HttpServer osrm;
    private static final AtomicBoolean OSRM_FAILING = new AtomicBoolean(false);

    private static final String OSRM_RESPONSE =
            """
      {"code":"Ok","routes":[{"distance":2274.4,"duration":237.2}]}
      """;

    @BeforeAll
    static void startOsrmStub() throws Exception {
        osrm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        osrm.createContext(
                "/route/v1/driving/",
                exchange -> {
                    if (OSRM_FAILING.get()) {
                        exchange.sendResponseHeaders(500, -1);
                        exchange.close();
                        return;
                    }
                    byte[] body = OSRM_RESPONSE.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                });
        osrm.start();
    }

    @AfterAll
    static void stopOsrmStub() {
        osrm.stop(0);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add(
                "enruta.routing.osrm-base-url",
                () -> "http://127.0.0.1:" + osrm.getAddress().getPort());
        registry.add("enruta.routing.cache-ttl", () -> "30s");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        OSRM_FAILING.set(false);
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void returnsOsrmResultAndThenServesItFromCache() throws Exception {
        mockMvc.perform(
                        get("/api/v1/eta")
                                .param("fromLat", "4.3449")
                                .param("fromLng", "-74.3674")
                                .param("toLat", "4.3374")
                                .param("toLng", "-74.3600")
                                .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("OSRM"))
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.distanceMeters").value(2274))
                .andExpect(jsonPath("$.durationSeconds").value(237));

        mockMvc.perform(
                        get("/api/v1/eta")
                                .param("fromLat", "4.3449")
                                .param("fromLng", "-74.3674")
                                .param("toLat", "4.3374")
                                .param("toLng", "-74.3600")
                                .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.source").value("CACHE"))
                .andExpect(jsonPath("$.distanceMeters").value(2274));
    }

    @Test
    void fallsBackWhenOsrmFails() throws Exception {
        OSRM_FAILING.set(true);

        mockMvc.perform(
                        get("/api/v1/eta")
                                .param("fromLat", "4.3520")
                                .param("fromLng", "-74.3600")
                                .param("toLat", "4.3350")
                                .param("toLng", "-74.3650")
                                .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FALLBACK"))
                .andExpect(jsonPath("$.distanceMeters").isNumber())
                .andExpect(jsonPath("$.durationSeconds").isNumber());
    }

    @Test
    void rejectsSameOriginAndDestination() throws Exception {
        mockMvc.perform(
                        get("/api/v1/eta")
                                .param("fromLat", "4.3449")
                                .param("fromLng", "-74.3674")
                                .param("toLat", "4.3449")
                                .param("toLng", "-74.3674")
                                .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_POINT"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(
                        get("/api/v1/eta")
                                .param("fromLat", "4.3449")
                                .param("fromLng", "-74.3674")
                                .param("toLat", "4.3374")
                                .param("toLng", "-74.3600"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cacheKeyRoundsToElevenMeters() {
        String first =
                com.enruta.routing.service.EtaService.cacheKey(
                        4.34491, -74.36741, 4.33741, -74.36001);
        String second =
                com.enruta.routing.service.EtaService.cacheKey(
                        4.34494, -74.36744, 4.33744, -74.36004);
        String other =
                com.enruta.routing.service.EtaService.cacheKey(
                        4.35000, -74.36740, 4.33740, -74.36000);

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(other);
        assertThat(first).startsWith("eta:");
    }
}
