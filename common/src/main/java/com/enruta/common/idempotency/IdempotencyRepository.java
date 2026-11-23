package com.enruta.common.idempotency;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {

    default Optional<IdempotencyRecord> findValid(String key, Instant now) {
        return findById(key).filter(record -> record.getExpiresAt().isAfter(now));
    }
}
