package com.enruta.tracking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.common.error.BusinessException;
import com.enruta.tracking.messaging.GpsProducer;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class LocationControllerTest {

    private static final int MAX_BATCHES_PER_SECOND = 3;

    @Mock private GpsProducer producer;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private Jwt jwt;

    private LocationController controller;
    private final UUID driverId = UUID.randomUUID();
    private final LocationBatchRequest batch =
            new LocationBatchRequest(List.of(new LocationBatchRequest.Point(4.34, -74.36)));

    @BeforeEach
    void setUp() {
        controller = new LocationController(producer, redis, MAX_BATCHES_PER_SECOND);
    }

    @Test
    void aDriverCanReportItsOwnPosition() {
        when(jwt.getSubject()).thenReturn(driverId.toString());
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:gps:" + driverId)).thenReturn(1L);

        ResponseEntity<Void> response = controller.ingest(driverId, batch, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNull();
        verify(redis).expire("rl:gps:" + driverId, Duration.ofSeconds(1));
        verify(producer).publish(driverId, batch.points());
    }

    @Test
    void aSecondBatchInTheSameSecondDoesNotResetTheWindow() {
        when(jwt.getSubject()).thenReturn(driverId.toString());
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:gps:" + driverId)).thenReturn(2L);

        ResponseEntity<Void> response = controller.ingest(driverId, batch, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void anAdminCanReportForAnotherDriver() {
        UUID other = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(other.toString());
        when(jwt.getClaimAsStringList("roles")).thenReturn(List.of("ADMIN"));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:gps:" + driverId)).thenReturn(1L);

        ResponseEntity<Void> response = controller.ingest(driverId, batch, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(producer).publish(driverId, batch.points());
    }

    @Test
    void aDriverCannotReportForSomebodyElse() {
        UUID other = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(other.toString());
        when(jwt.getClaimAsStringList("roles")).thenReturn(List.of("DRIVER"));

        assertThatThrownBy(() -> controller.ingest(driverId, batch, jwt))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> {
                            assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                            assertThat(ex.getCode()).isEqualTo("FORBIDDEN");
                        });
        verify(producer, never()).publish(any(), any());
    }

    @Test
    void aTokenWithoutRolesCannotReportForAnotherDriver() {
        UUID other = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(other.toString());
        when(jwt.getClaimAsStringList("roles")).thenReturn(null);

        assertThatThrownBy(() -> controller.ingest(driverId, batch, jwt))
                .isInstanceOf(BusinessException.class);
        verify(producer, never()).publish(any(), any());
    }

    @Test
    void exceedingTheRateLimitIsRejected() {
        when(jwt.getSubject()).thenReturn(driverId.toString());
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:gps:" + driverId))
                .thenReturn((long) MAX_BATCHES_PER_SECOND + 1);

        assertThatThrownBy(() -> controller.ingest(driverId, batch, jwt))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> {
                            assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                            assertThat(ex.getCode()).isEqualTo("RATE_LIMITED");
                        });
        verify(producer, never()).publish(any(), any());
    }

    @Test
    void aRedisCounterFailureDoesNotBlockTheIngestion() {
        when(jwt.getSubject()).thenReturn(driverId.toString());
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:gps:" + driverId)).thenReturn(null);

        ResponseEntity<Void> response = controller.ingest(driverId, batch, jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(redis, never()).expire(anyString(), any(Duration.class));
        verify(producer).publish(eq(driverId), eq(batch.points()));
    }
}
