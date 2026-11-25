package com.enruta.routing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class EtaServiceTest {

    @Mock private OsrmClient osrm;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private EtaService service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Duration ttl = Duration.ofSeconds(20);

    @BeforeEach
    void setUp() {
        service = new EtaService(osrm, redis, objectMapper, new SimpleMeterRegistry(), ttl);
    }

    @Test
    void aCachedRouteDoesNotCallOsrm() throws Exception {
        String key = EtaService.cacheKey(4.3449, -74.3674, 4.3374, -74.36);
        String cached = objectMapper.writeValueAsString(new RouteResult(2529, 273, false, "OSRM"));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(key)).thenReturn(cached);

        RouteResult result = service.route(4.3449, -74.3674, 4.3374, -74.36);

        assertThat(result.cached()).isTrue();
        assertThat(result.source()).isEqualTo("CACHE");
        assertThat(result.distanceMeters()).isEqualTo(2529);
        verify(osrm, never()).route(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void aMissCallsOsrmAndStoresTheResult() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(osrm.route(4.3449, -74.3674, 4.3374, -74.36))
                .thenReturn(new RouteResult(2529, 273, false, "OSRM"));

        RouteResult result = service.route(4.3449, -74.3674, 4.3374, -74.36);

        assertThat(result.cached()).isFalse();
        assertThat(result.source()).isEqualTo("OSRM");
        verify(valueOps).set(anyString(), anyString(), eq(ttl));
    }

    @Test
    void aFallbackIsStillReturnedToTheCaller() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(osrm.route(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteResult(1970, 369, false, "FALLBACK"));

        RouteResult result = service.route(4.352, -74.36, 4.335, -74.365);

        assertThat(result.source()).isEqualTo("FALLBACK");
        assertThat(result.durationSeconds()).isPositive();
    }

    @Test
    void anUnreadableCacheEntryIsRecalculated() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("esto-no-es-json");
        when(osrm.route(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteResult(1000, 120, false, "OSRM"));

        RouteResult result = service.route(4.3449, -74.3674, 4.3374, -74.36);

        assertThat(result.source()).isEqualTo("OSRM");
        assertThat(result.cached()).isFalse();
    }

    @Test
    void cacheKeyRoundsToElevenMeters() {
        assertThat(EtaService.cacheKey(4.34491, -74.36741, 4.33741, -74.36001))
                .isEqualTo(EtaService.cacheKey(4.34494, -74.36744, 4.33744, -74.36004))
                .isNotEqualTo(EtaService.cacheKey(4.35, -74.3674, 4.3374, -74.36));
    }
}
