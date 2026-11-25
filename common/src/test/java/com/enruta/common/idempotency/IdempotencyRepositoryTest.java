package com.enruta.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IdempotencyRepositoryTest {

    private final IdempotencyRepository repository =
            mock(IdempotencyRepository.class, CALLS_REAL_METHODS);

    @Test
    void findValidReturnsUnexpiredRecords() {
        Instant now = Instant.now();
        IdempotencyRecord record =
                new IdempotencyRecord("clave-1", "hash", 200, "{}", now, now.plusSeconds(60));
        when(repository.findById("clave-1")).thenReturn(Optional.of(record));

        assertThat(repository.findValid("clave-1", now)).contains(record);
    }

    @Test
    void findValidFiltersExpiredRecords() {
        Instant now = Instant.now();
        IdempotencyRecord record =
                new IdempotencyRecord(
                        "clave-1", "hash", 200, "{}", now.minusSeconds(120), now.minusSeconds(60));
        when(repository.findById("clave-1")).thenReturn(Optional.of(record));

        assertThat(repository.findValid("clave-1", now)).isEmpty();
    }
}
