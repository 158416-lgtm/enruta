package com.enruta.notification.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Politica de reintentos: cuantos intentos y cuanto esperar entre ellos.
 *
 * <p>Es una funcion pura (sin reloj ni base de datos) para poder probar los bordes: primer fallo,
 * ultimo fallo permitido y agotamiento.
 */
@Component
public class RetryPolicy {

    private static final Duration LAST_DELAY = Duration.ofSeconds(30);

    private final int maxAttempts;
    private final List<Duration> backoff;

    public RetryPolicy(
            @Value("${enruta.notification.max-attempts:3}") int maxAttempts,
            @Value("${enruta.notification.backoff:2s,10s,30s}") List<Duration> backoff) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("max-attempts debe ser >= 1");
        }
        this.maxAttempts = maxAttempts;
        this.backoff = List.copyOf(backoff);
    }

    public record Decision(boolean exhausted, Instant nextAttemptAt, int attemptNumber) {}

    /**
     * @param attemptsAfterFailure intentos ya consumidos (incluido el que acaba de fallar)
     */
    public Decision decide(int attemptsAfterFailure, Instant now) {
        if (attemptsAfterFailure >= maxAttempts) {
            return new Decision(true, null, attemptsAfterFailure);
        }
        Duration delay =
                backoff.isEmpty()
                        ? LAST_DELAY
                        : backoff.get(Math.min(attemptsAfterFailure - 1, backoff.size() - 1));
        return new Decision(false, now.plus(delay), attemptsAfterFailure);
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}
