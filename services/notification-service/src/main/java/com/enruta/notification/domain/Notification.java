package com.enruta.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Notificacion persistida antes de intentar el envio.
 *
 * <p>La fila es el "outbox de salida": si SMTP falla, el estado y el proximo intento sobreviven a
 * un reinicio del servicio.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum Status {
        PENDING,
        SENT,
        FAILED
    }

    @Id private UUID id;

    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(nullable = false, length = 16)
    private String channel;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(nullable = false, length = 64)
    private String template;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String variables;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private short attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Notification() {}

    public Notification(
            UUID shipmentId, String channel, String recipient, String template, String variables) {
        this.id = UUID.randomUUID();
        this.shipmentId = shipmentId;
        this.channel = channel;
        this.recipient = recipient;
        this.template = template;
        this.variables = variables;
        this.status = Status.PENDING;
        this.attempts = 0;
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
    }

    public void registerAttempt() {
        this.attempts = (short) (this.attempts + 1);
    }

    public void markSent() {
        this.status = Status.SENT;
        this.sentAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void scheduleRetry(Instant when, String error) {
        this.status = Status.PENDING;
        this.nextAttemptAt = when;
        this.lastError = truncate(error);
    }

    public void markFailed(String error) {
        this.status = Status.FAILED;
        this.nextAttemptAt = null;
        this.lastError = truncate(error);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    public UUID getId() {
        return id;
    }

    public UUID getShipmentId() {
        return shipmentId;
    }

    public String getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getTemplate() {
        return template;
    }

    public String getVariables() {
        return variables;
    }

    public Status getStatus() {
        return status;
    }

    public short getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
