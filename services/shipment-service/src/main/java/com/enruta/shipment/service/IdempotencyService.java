package com.enruta.shipment.service;

import com.enruta.common.idempotency.IdempotencyRecord;
import com.enruta.common.idempotency.IdempotencyRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotencia de escritura con reclamacion previa y atomica.
 *
 * <p>La reclamacion es un {@code INSERT ... ON CONFLICT DO NOTHING} en su propia transaccion:
 * exactamente una peticion gana la clave, incluso bajo concurrencia. Se evita {@code save()} de JPA
 * porque su semantica de merge convierte el conflicto en una actualizacion silenciosa.
 */
@Service
public class IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);
    private static final int IN_PROGRESS = 0;

    private final IdempotencyRepository repository;
    private final JdbcTemplate jdbc;

    public IdempotencyService(IdempotencyRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    /**
     * @return true si esta peticion gano la clave; false si ya existia.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String key, String requestHash) {
        Instant now = Instant.now();
        int inserted =
                jdbc.update(
                        """
            INSERT INTO idempotency_keys
              (key, request_hash, response_status, response_body, created_at, expires_at)
            VALUES (?, ?, ?, NULL, ?, ?)
            ON CONFLICT (key) DO NOTHING
            """,
                        key,
                        requestHash,
                        IN_PROGRESS,
                        Timestamp.from(now),
                        Timestamp.from(now.plus(TTL)));
        return inserted == 1;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> find(String key) {
        return repository.findValid(key, Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key, int status, String body) {
        jdbc.update(
                "UPDATE idempotency_keys SET response_status = ?, response_body = ?::jsonb WHERE key = ?",
                status,
                body,
                key);
    }

    /** Libera la clave cuando el caso de uso fallo, para que el cliente pueda reintentar. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String key) {
        jdbc.update("DELETE FROM idempotency_keys WHERE key = ?", key);
    }

    public boolean isInProgress(IdempotencyRecord record) {
        return record.getResponseStatus() != null && record.getResponseStatus() == IN_PROGRESS;
    }
}
