package com.enruta.common.outbox;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica los eventos del outbox a Kafka.
 *
 * <p>Se marca como publicado solo despues del ack del broker. Si falla, la fila queda pendiente y
 * el siguiente tick reintenta; {@code attempts} deja rastro para diagnosticar.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int MAX_PER_TICK = 100;
    private static final int MAX_ATTEMPTS = 10;

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(
            fixedDelayString = "${enruta.outbox.poll-interval-ms:500}",
            initialDelayString = "${enruta.outbox.initial-delay-ms:0}")
    @SchedulerLock(name = "outbox-relay", lockAtMostFor = "PT30S", lockAtLeastFor = "PT0S")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = repository.lockPending(MAX_PER_TICK);
        if (pending.isEmpty()) {
            return;
        }
        for (OutboxEvent event : pending) {
            if (event.getAttempts() >= MAX_ATTEMPTS) {
                log.error(
                        "outbox event={} supero {} intentos, requiere intervencion",
                        event.getId(),
                        MAX_ATTEMPTS);
                continue;
            }
            try {
                kafkaTemplate
                        .send(
                                event.getTopic(),
                                event.getAggregateId().toString(),
                                event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                event.markPublished(Instant.now());
                log.debug("outbox publicado id={} type={}", event.getId(), event.getEventType());
            } catch (Exception ex) {
                event.registerAttempt();
                log.warn(
                        "no se pudo publicar outbox id={} type={} intento={}",
                        event.getId(),
                        event.getEventType(),
                        event.getAttempts(),
                        ex);
            }
        }
    }
}
