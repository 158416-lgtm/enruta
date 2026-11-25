package com.enruta.routing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.routing.service.NominatimClient;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class GeocodeControllerTest {

    @Mock private NominatimClient nominatim;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;

    private GeocodeController controller;

    @BeforeEach
    void setUp() {
        controller = new GeocodeController(nominatim, redis);
    }

    @Test
    void returnsCachedCoordinatesWithoutCallingNominatim() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("geocode:fusagasuga"))
                .thenReturn("4.3449|-74.3674|Fusagasuga, Cundinamarca");

        GeocodeController.GeocodeResponse response = controller.geocode("Fusagasuga");

        assertThat(response.cached()).isTrue();
        assertThat(response.source()).isEqualTo("CACHE");
        assertThat(response.lat()).isEqualTo(4.3449);
        verify(nominatim, org.mockito.Mockito.never()).search(anyString());
    }

    @Test
    void callsNominatimOnMissAndCachesFor24Hours() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        when(nominatim.search("Fusagasuga"))
                .thenReturn(new NominatimClient.Place(4.3449, -74.3674, "Fusagasuga"));

        GeocodeController.GeocodeResponse response = controller.geocode("Fusagasuga");

        assertThat(response.source()).isEqualTo("NOMINATIM");
        assertThat(response.cached()).isFalse();
        assertThat(response.lat()).isEqualTo(4.3449);
        verify(values).set(eq("geocode:fusagasuga"), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void noMatchReturnsEmptyCoordinates() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        when(nominatim.search("sitio inexistente")).thenReturn(null);

        GeocodeController.GeocodeResponse response = controller.geocode("sitio inexistente");

        assertThat(response.lat()).isNull();
        assertThat(response.lng()).isNull();
    }

    @Test
    void corruptCacheEntryIsRecalculated() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("basura");
        when(nominatim.search("Fusagasuga"))
                .thenReturn(new NominatimClient.Place(4.3449, -74.3674, "Fusagasuga"));

        GeocodeController.GeocodeResponse response = controller.geocode("Fusagasuga");

        assertThat(response.source()).isEqualTo("NOMINATIM");
        assertThat(response.cached()).isFalse();
    }

    @Test
    void redisFailureDoesNotBreakTheResponse() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        doThrow(new RuntimeException("redis caido"))
                .when(values)
                .set(anyString(), anyString(), any());
        when(nominatim.search("Fusagasuga"))
                .thenReturn(new NominatimClient.Place(4.3449, -74.3674, "Fusagasuga"));

        GeocodeController.GeocodeResponse response = controller.geocode("Fusagasuga");

        assertThat(response.lat()).isEqualTo(4.3449);
        assertThat(response.cached()).isFalse();
    }
}
