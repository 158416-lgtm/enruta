package com.enruta.notification.service;

import com.enruta.contracts.Topics;
import com.enruta.notification.domain.Notification;
import com.enruta.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistencia + envio + reintentos.
 *
 * <p>Primero se guarda la fila y despues se intenta enviar: si el proceso muere entre ambas cosas,
 * el scheduler lo reintenta. Tras agotar los intentos se publica en la DLQ para inspeccion manual.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_PER_TICK = 50;

    private final NotificationRepository repository;
    private final EmailSender sender;
    private final RetryPolicy retryPolicy;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final Counter sentCounter;
    private final Counter retryCounter;
    private final Counter failedCounter;

    public NotificationService(
            NotificationRepository repository,
            EmailSender sender,
            RetryPolicy retryPolicy,
            KafkaTemplate<String, String> kafka,
            ObjectMapper objectMapper,
            MeterRegistry meters) {
        this.repository = repository;
        this.sender = sender;
        this.retryPolicy = retryPolicy;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.sentCounter =
                Counter.builder("notification_attempts_total")
                        .tag("result", "sent")
                        .register(meters);
        this.retryCounter =
                Counter.builder("notification_attempts_total")
                        .tag("result", "retry")
                        .register(meters);
        this.failedCounter =
                Counter.builder("notification_attempts_total")
                        .tag("result", "dead_letter")
                        .register(meters);
    }

    @Transactional
    public Notification createAndAttempt(
            UUID shipmentId,
            String channel,
            String recipient,
            String template,
            String variablesJson) {
        Notification notification =
                repository.save(
                        new Notification(shipmentId, channel, recipient, template, variablesJson));
        attempt(notification);
        return notification;
    }

    /** Lo llama el scheduler: reserva las vencidas y las procesa. */
    @Transactional
    public int processDue() {
        List<Notification> due = repository.lockDue(MAX_PER_TICK);
        due.forEach(this::attempt);
        return due.size();
    }

    @Transactional
    public Notification attemptNow(UUID id) {
        Notification notification =
                repository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        com.enruta.common.error.BusinessException.notFound(
                                                "NOTIFICATION_NOT_FOUND",
                                                "Notificacion inexistente"));
        if (notification.getStatus() == Notification.Status.PENDING) {
            attempt(notification);
        }
        return notification;
    }

    @Transactional(readOnly = true)
    public List<Notification> byShipment(UUID shipmentId) {
        return repository.findByShipmentIdOrderByCreatedAtDesc(shipmentId);
    }

    @Transactional(readOnly = true)
    public Notification find(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(
                        () ->
                                com.enruta.common.error.BusinessException.notFound(
                                        "NOTIFICATION_NOT_FOUND", "Notificacion inexistente"));
    }

    private void attempt(Notification notification) {
        notification.registerAttempt();
        try {
            sender.send(notification);
            notification.markSent();
            log.info(
                    "notificacion enviada id={} to={} intento={}",
                    notification.getId(),
                    notification.getRecipient(),
                    notification.getAttempts());
        } catch (Exception ex) {
            RetryPolicy.Decision decision =
                    retryPolicy.decide(notification.getAttempts(), Instant.now());
            if (decision.exhausted()) {
                notification.markFailed(ex.getMessage());
                publishToDlq(notification, ex);
                log.error(
                        "notificacion a DLQ id={} tras {} intentos: {}",
                        notification.getId(),
                        notification.getAttempts(),
                        ex.getMessage());
            } else {
                retryCounter.increment();
                notification.scheduleRetry(decision.nextAttemptAt(), ex.getMessage());
                log.warn(
                        "notificacion reintentara id={} intento={} en {}: {}",
                        notification.getId(),
                        notification.getAttempts(),
                        decision.nextAttemptAt(),
                        ex.getMessage());
            }
        }
        repository.save(notification);
    }

    private void publishToDlq(Notification notification, Exception cause) {
        try {
            Map<String, Object> payload =
                    Map.of(
                            "notificationId", notification.getId().toString(),
                            "shipmentId",
                                    notification.getShipmentId() == null
                                            ? ""
                                            : notification.getShipmentId().toString(),
                            "recipient", notification.getRecipient(),
                            "template", notification.getTemplate(),
                            "attempts", notification.getAttempts(),
                            "error", String.valueOf(cause.getMessage()));
            kafka.send(
                    Topics.dlqOf(Topics.NOTIFICATION_COMMANDS),
                    notification.getId().toString(),
                    objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.error("no se pudo publicar en la DLQ", ex);
        }
    }
}
