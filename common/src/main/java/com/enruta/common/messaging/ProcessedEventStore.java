package com.enruta.common.messaging;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Deduplicacion de eventos consumidos.
 *
 * <p>Kafka garantiza al-menos-una-vez: un reinicio o un rebalance puede repetir un mensaje. El
 * {@code INSERT ... ON CONFLICT DO NOTHING} hace que solo el primer consumidor lo procese.
 */
public class ProcessedEventStore {

    private final JdbcTemplate jdbc;

    public ProcessedEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean markIfNew(UUID eventId, String consumer) {
        int inserted =
                jdbc.update(
                        "INSERT INTO processed_events (event_id, consumer, processed_at) "
                                + "VALUES (?, ?, now()) ON CONFLICT DO NOTHING",
                        eventId,
                        consumer);
        return inserted == 1;
    }
}
