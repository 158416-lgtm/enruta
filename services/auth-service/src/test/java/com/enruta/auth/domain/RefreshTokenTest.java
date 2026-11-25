package com.enruta.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    @Test
    void newTokenIsUsableAndExposesItsData() {
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(600);

        RefreshToken token = new RefreshToken(userId, "hash", expiresAt);

        assertThat(token.getId()).isNotNull();
        assertThat(token.getUserId()).isEqualTo(userId);
        assertThat(token.getTokenHash()).isEqualTo("hash");
        assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.isUsable(Instant.now())).isTrue();
    }

    @Test
    void revokingMakesItUnusable() {
        RefreshToken token =
                new RefreshToken(UUID.randomUUID(), "hash", Instant.now().plusSeconds(600));

        token.revoke();

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.isUsable(Instant.now())).isFalse();
    }

    @Test
    void expiredTokensAreNotUsable() {
        RefreshToken token =
                new RefreshToken(UUID.randomUUID(), "hash", Instant.now().minusSeconds(1));

        assertThat(token.isUsable(Instant.now())).isFalse();
    }

    @Test
    void jpaConstructorIsAvailableViaReflection() throws Exception {
        var constructor = RefreshToken.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(constructor.newInstance()).isNotNull();
    }
}
