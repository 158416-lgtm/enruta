package com.enruta.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enruta.common.idempotency.IdempotencyRepository;
import com.enruta.common.outbox.OutboxRepository;
import com.enruta.shipment.repository.ShipmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de integracion real: PostgreSQL de Testcontainers, migraciones Flyway y validacion de
 * esquema por Hibernate. El relay del outbox se desactiva (initial-delay enorme) porque aqui
 * interesa el invariante "cambio + evento en la misma transaccion", no la publicacion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class ShipmentApiTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("shipmentdb");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("enruta.outbox.initial-delay-ms", () -> "3600000");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ShipmentRepository shipments;
    @Autowired private OutboxRepository outbox;
    @Autowired private IdempotencyRepository idempotency;

    private UUID clientId;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        shipments.deleteAll();
        idempotency.deleteAll();
        clientId = UUID.randomUUID();
    }

    @Test
    void createsShipmentPersistsOutboxAndIsIdempotent() throws Exception {
        String body = requestBody("Parque Principal Fusagasuga", "Terminal de Transportes");
        String key = UUID.randomUUID().toString();

        MvcResult first =
                mockMvc.perform(
                                post("/api/v1/shipments")
                                        .with(clientJwt())
                                        .header("Idempotency-Key", key)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("CREATED"))
                        .andExpect(
                                jsonPath("$.pickupPin")
                                        .value(org.hamcrest.Matchers.matchesPattern("\\d{4}")))
                        .andReturn();

        String shipmentId =
                objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        // Replay con la misma clave y el mismo cuerpo: misma respuesta, cero filas extra.
        mockMvc.perform(
                        post("/api/v1/shipments")
                                .with(clientJwt())
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(shipmentId));

        assertThat(shipments.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(outbox.findAll().getFirst().getEventType()).isEqualTo("shipment.created");
        assertThat(outbox.findAll().getFirst().getPublishedAt()).isNull();

        // La misma clave con otro cuerpo es un error del cliente.
        mockMvc.perform(
                        post("/api/v1/shipments")
                                .with(clientJwt())
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody("Otra direccion", "Otro destino")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void requiresIdempotencyKey() throws Exception {
        mockMvc.perform(
                        post("/api/v1/shipments")
                                .with(clientJwt())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody("A", "B")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
    }

    @Test
    void rejectsInvalidCoordinates() throws Exception {
        mockMvc.perform(
                        post("/api/v1/shipments")
                                .with(clientJwt())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                    {"pickup":{"lat":123.0,"lng":-74.36,"address":"X"},
                     "dropoff":{"lat":4.33,"lng":-74.35,"address":"Y"}}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void ownerCanReadButAnotherClientCannot() throws Exception {
        String id = createShipment(clientId);

        mockMvc.perform(get("/api/v1/shipments/{id}", id).with(clientJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.history[0].to").value("CREATED"));

        mockMvc.perform(get("/api/v1/shipments/{id}", id).with(jwtFor(UUID.randomUUID(), "CLIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void cancelIsAllowedOnceAndThenConflicts() throws Exception {
        String id = createShipment(clientId);

        mockMvc.perform(post("/api/v1/shipments/{id}/cancel", id).with(clientJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/shipments/{id}/cancel", id).with(clientJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

        assertThat(outbox.findAll())
                .extracting(e -> e.getEventType())
                .containsExactlyInAnyOrder("shipment.created", "shipment.status-changed");
    }

    @Test
    void listShowsOwnShipmentsToClientsAndEverythingToAdmins() throws Exception {
        String cancelled = createShipment(clientId);
        createShipment(clientId);
        mockMvc.perform(post("/api/v1/shipments/{id}/cancel", cancelled).with(clientJwt()))
                .andExpect(status().isOk());

        // El cliente solo ve los suyos.
        mockMvc.perform(get("/api/v1/shipments").param("size", "50").with(clientJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // El cliente puede filtrar por estado.
        mockMvc.perform(get("/api/v1/shipments").param("status", "CANCELLED").with(clientJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(cancelled));

        // El admin ve todo, con y sin filtro.
        var admin = jwtFor(UUID.randomUUID(), "ADMIN");
        mockMvc.perform(get("/api/v1/shipments").param("size", "50").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/shipments").param("status", "CREATED").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void historyReturnsTheTimelineAndChecksPermissions() throws Exception {
        String id = createShipment(clientId);

        mockMvc.perform(get("/api/v1/shipments/{id}/history", id).with(clientJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toStatus").value("CREATED"))
                .andExpect(jsonPath("$[0].reason").value("envio creado"));

        mockMvc.perform(
                        get("/api/v1/shipments/{id}/history", id)
                                .with(jwtFor(UUID.randomUUID(), "CLIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminsCanAssignManually() throws Exception {
        String id = createShipment(clientId);
        UUID driverId = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/v1/shipments/{id}/assign", id)
                                .param("driverId", driverId.toString())
                                .with(clientJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(
                        post("/api/v1/shipments/{id}/assign", id)
                                .param("driverId", driverId.toString())
                                .with(jwtFor(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.driverId").value(driverId.toString()));

        // Un envio ya asignado no se puede volver a asignar.
        mockMvc.perform(
                        post("/api/v1/shipments/{id}/assign", id)
                                .param("driverId", UUID.randomUUID().toString())
                                .with(jwtFor(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGN_NOT_ALLOWED"));
    }

    @Test
    void summaryCountsShipmentsByStatus() throws Exception {
        String cancelled = createShipment(clientId);
        createShipment(clientId);
        mockMvc.perform(post("/api/v1/shipments/{id}/cancel", cancelled).with(clientJwt()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/shipments/summary").with(clientJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.CREATED").value(1))
                .andExpect(jsonPath("$.CANCELLED").value(1));
    }

    @Test
    void assignedDriverCanConfirmPickupManually() throws Exception {
        String id = createShipment(clientId);
        UUID driverId = UUID.randomUUID();
        var admin = jwtFor(UUID.randomUUID(), "ADMIN");
        mockMvc.perform(
                        post("/api/v1/shipments/{id}/assign", id)
                                .param("driverId", driverId.toString())
                                .with(admin))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/shipments/{id}/confirm-pickup", id).with(clientJwt()))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/v1/shipments/{id}/confirm-pickup", id)
                                .with(jwtFor(driverId, "DRIVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));
    }

    private String createShipment(UUID customerId) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/shipments")
                                        .with(jwtFor(customerId, "CLIENT"))
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestBody("Origen", "Destino")))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String requestBody(String pickupAddress, String dropoffAddress) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of(
                        "pickup", Map.of("lat", 4.3449, "lng", -74.3674, "address", pickupAddress),
                        "dropoff",
                                Map.of("lat", 4.3374, "lng", -74.3600, "address", dropoffAddress)));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor clientJwt() {
        return jwtFor(clientId, "CLIENT");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(
            UUID subject, String... roles) {
        return jwt().jwt(
                        builder ->
                                builder.subject(subject.toString()).claim("roles", List.of(roles)));
    }
}
