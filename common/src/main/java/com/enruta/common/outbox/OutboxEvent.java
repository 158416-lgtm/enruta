package com.enruta.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Evento pendiente de publicar en Kafka.
 *
 * <p>Se escribe en la <em>misma transaccion</em> que el cambio de estado, de modo que no puede
 * existir un cambio sin evento ni un evento sin cambio (patron outbox).
 */
@Entity
@Table(name = "outbox")
public class OutboxEvent {

    @Id private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 64)
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private short attempts;

    protected OutboxEvent() {}

    public OutboxEvent(
            UUID id,
            UUID aggregateId,
            String eventType,
            String topic,
            String payload,
            Instant createdAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.createdAt = createdAt;
        this.attempts = 0;
    }

    public void markPublished(Instant when) {
        this.publishedAt = when;
    }

    public void registerAttempt() {
        this.attempts = (short) (this.attempts + 1);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public short getAttempts() {
        return attempts;
    }
}
