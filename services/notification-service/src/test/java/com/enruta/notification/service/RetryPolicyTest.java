package com.enruta.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final Instant now = Instant.parse("2026-11-23T12:00:00Z");

    @Test
    void firstFailureSchedulesTheFirstBackoff() {
        RetryPolicy policy =
                new RetryPolicy(3, List.of(Duration.ofSeconds(2), Duration.ofSeconds(10)));

        RetryPolicy.Decision decision = policy.decide(1, now);

        assertThat(decision.exhausted()).isFalse();
        assertThat(decision.nextAttemptAt()).isEqualTo(now.plusSeconds(2));
    }

    @Test
    void lastAllowedFailureSchedulesTheLastBackoff() {
        RetryPolicy policy =
                new RetryPolicy(3, List.of(Duration.ofSeconds(2), Duration.ofSeconds(10)));

        RetryPolicy.Decision decision = policy.decide(2, now);

        assertThat(decision.exhausted()).isFalse();
        assertThat(decision.nextAttemptAt()).isEqualTo(now.plusSeconds(10));
    }

    @Test
    void reachingMaxAttemptsExhaustsThePolicy() {
        RetryPolicy policy = new RetryPolicy(3, List.of(Duration.ofSeconds(2)));

        RetryPolicy.Decision decision = policy.decide(3, now);

        assertThat(decision.exhausted()).isTrue();
        assertThat(decision.nextAttemptAt()).isNull();
    }

    @Test
    void singleAttemptPolicyGoesStraightToDeadLetter() {
        RetryPolicy policy = new RetryPolicy(1, List.of(Duration.ofSeconds(2)));

        assertThat(policy.decide(1, now).exhausted()).isTrue();
    }

    @Test
    void emptyBackoffFallsBackToADefaultDelay() {
        RetryPolicy policy = new RetryPolicy(3, List.of());

        assertThat(policy.decide(1, now).nextAttemptAt()).isAfter(now);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new RetryPolicy(0, List.of(Duration.ofSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
