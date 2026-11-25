package com.enruta.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IdempotencyRecordTest {

    @Test
    void storesTheRequestFingerprintAndTheResponse() {
        Instant createdAt = Instant.now().minusSeconds(5);
        Instant expiresAt = Instant.now().plusSeconds(3600);

        IdempotencyRecord record =
                new IdempotencyRecord(
                        "clave-1", "fingerprint", 201, "{\"id\":1}", createdAt, expiresAt);

        assertThat(record.getKey()).isEqualTo("clave-1");
        assertThat(record.getRequestHash()).isEqualTo("fingerprint");
        assertThat(record.getResponseStatus()).isEqualTo(201);
        assertThat(record.getResponseBody()).isEqualTo("{\"id\":1}");
        assertThat(record.getCreatedAt()).isEqualTo(createdAt);
        assertThat(record.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void completeOverwritesTheStoredResponse() {
        IdempotencyRecord record =
                new IdempotencyRecord(
                        "clave-1",
                        "fingerprint",
                        102,
                        null,
                        Instant.now(),
                        Instant.now().plusSeconds(3600));

        record.complete(200, "{\"ok\":true}");

        assertThat(record.getResponseStatus()).isEqualTo(200);
        assertThat(record.getResponseBody()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void jpaConstructorIsAvailableViaReflection() throws Exception {
        var constructor = IdempotencyRecord.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(constructor.newInstance()).isNotNull();
    }
}
