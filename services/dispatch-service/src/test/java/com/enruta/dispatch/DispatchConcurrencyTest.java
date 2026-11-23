package com.enruta.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.enruta.common.error.BusinessException;
import com.enruta.dispatch.domain.Assignment;
import com.enruta.dispatch.domain.DispatchState;
import com.enruta.dispatch.domain.Driver;
import com.enruta.dispatch.domain.Offer;
import com.enruta.dispatch.repository.AssignmentRepository;
import com.enruta.dispatch.repository.DispatchStateRepository;
import com.enruta.dispatch.repository.DriverRepository;
import com.enruta.dispatch.repository.OfferRepository;
import com.enruta.dispatch.service.DispatchService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * El test que justifica la doble barrera: 50 hilos aceptan la misma oferta al mismo tiempo.
 *
 * <p>Resultado exigido: exactamente una aceptacion, una asignacion activa y 49 conflictos. Si
 * alguien quita el lock de Redis o la actualizacion condicional, este test falla.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DispatchConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("dispatchdb");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "32");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("enruta.outbox.initial-delay-ms", () -> "3600000");
        registry.add("enruta.dispatch.seed", () -> "false");
    }

    @Autowired private DispatchService dispatchService;
    @Autowired private DriverRepository drivers;
    @Autowired private OfferRepository offers;
    @Autowired private AssignmentRepository assignments;
    @Autowired private DispatchStateRepository states;

    private UUID offerId;
    private UUID driverId;
    private UUID shipmentId;

    @BeforeEach
    void setUp() {
        assignments.deleteAll();
        offers.deleteAll();
        states.deleteAll();
        drivers.deleteAll();

        shipmentId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        drivers.save(new Driver(driverId, "Concurrente", "MOTO", 4.5, 4.3449, -74.3674));
        states.save(new DispatchState(shipmentId, 4.3449, -74.3674));
        Offer offer =
                offers.save(
                        new Offer(
                                shipmentId,
                                driverId,
                                (short) 1,
                                500,
                                0.9,
                                Instant.now().plusSeconds(30)));
        offerId = offer.getId();
    }

    @Test
    void exactlyOneOfFiftyConcurrentAcceptsWins() throws Exception {
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(threads);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(
                    pool.submit(
                            () -> {
                                ready.countDown();
                                try {
                                    start.await();
                                    dispatchService.accept(offerId, driverId, List.of("DRIVER"));
                                    wins.incrementAndGet();
                                } catch (BusinessException expected) {
                                    conflicts.incrementAndGet();
                                } catch (Exception unexpectedError) {
                                    unexpected.incrementAndGet();
                                }
                            }));
        }

        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(wins.get()).as("aceptaciones exitosas").isEqualTo(1);
        assertThat(conflicts.get()).as("conflictos").isEqualTo(threads - 1);
        assertThat(unexpected.get()).as("errores inesperados").isZero();
        assertThat(assignments.count()).isEqualTo(1);
        assertThat(assignments.findFirstByShipmentIdAndStatus(shipmentId, Assignment.Status.ACTIVE))
                .isPresent();
    }
}
