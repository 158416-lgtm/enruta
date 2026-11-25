package com.enruta.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoSearchCommandArgs;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.Metrics;

@ExtendWith(MockitoExtension.class)
class CandidateFinderTest {

    @Mock private StringRedisTemplate redis;
    @Mock private GeoOperations<String, String> geo;

    private CandidateFinder finder;

    @BeforeEach
    void setUp() {
        finder = new CandidateFinder(redis);
    }

    @Test
    void nearestMapsGeoResultsToCandidates() {
        UUID near = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        stubSearch(
                List.of(
                        geoResult(near, new Distance(120.7, Metrics.METERS)),
                        geoResult(far, new Distance(250.2, Metrics.METERS))));

        List<CandidateFinder.Candidate> candidates = finder.nearest(4.34, -74.36, 1500, 8);

        assertThat(candidates)
                .containsExactly(
                        new CandidateFinder.Candidate(near, 120),
                        new CandidateFinder.Candidate(far, 250));
    }

    @Test
    void nearestIgnoresEntriesWhoseNameIsNotAUuid() {
        stubSearch(
                List.of(
                        new GeoResult<>(
                                new GeoLocation<>("not-a-uuid", new Point(0, 0)),
                                new Distance(10, Metrics.METERS)),
                        geoResult(UUID.randomUUID(), new Distance(20, Metrics.METERS))));

        assertThat(finder.nearest(4.34, -74.36, 1500, 8)).hasSize(1);
    }

    @Test
    void nearestTreatsMissingDistanceAsZeroAndEmptyResultAsNoCandidates() {
        UUID driverId = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        GeoResult<GeoLocation<String>> withoutDistance = mock(GeoResult.class);
        when(withoutDistance.getContent())
                .thenReturn(new GeoLocation<>(driverId.toString(), new Point(0, 0)));
        when(withoutDistance.getDistance()).thenReturn(null);
        stubSearch(List.of(withoutDistance));

        assertThat(finder.nearest(4.34, -74.36, 1500, 8))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.distanceMeters()).isZero());

        when(geo.search(
                        eq(CandidateFinder.AVAILABLE_KEY),
                        any(GeoReference.class),
                        any(Distance.class),
                        any(GeoSearchCommandArgs.class)))
                .thenReturn(null);

        assertThat(finder.nearest(4.34, -74.36, 1500, 8)).isEmpty();
    }

    @Test
    void indexAddsTheDriverToTheGeoSet() {
        UUID driverId = UUID.randomUUID();
        when(redis.opsForGeo()).thenReturn(geo);

        finder.index(driverId, 4.34, -74.36);

        verify(geo)
                .add(CandidateFinder.AVAILABLE_KEY, new Point(-74.36, 4.34), driverId.toString());
    }

    @Test
    void unindexRemovesTheDriverFromTheGeoSet() {
        UUID driverId = UUID.randomUUID();
        when(redis.opsForGeo()).thenReturn(geo);

        finder.unindex(driverId);

        verify(geo).remove(CandidateFinder.AVAILABLE_KEY, driverId.toString());
    }

    @Test
    void clearDeletesTheGeoSet() {
        finder.clear();

        verify(redis).delete(CandidateFinder.AVAILABLE_KEY);
    }

    private void stubSearch(List<GeoResult<GeoLocation<String>>> results) {
        when(redis.opsForGeo()).thenReturn(geo);
        when(geo.search(
                        eq(CandidateFinder.AVAILABLE_KEY),
                        any(GeoReference.class),
                        any(Distance.class),
                        any(GeoSearchCommandArgs.class)))
                .thenReturn(new GeoResults<>(results, new Distance(0, Metrics.METERS)));
    }

    private static GeoResult<GeoLocation<String>> geoResult(UUID driverId, Distance distance) {
        return new GeoResult<>(new GeoLocation<>(driverId.toString(), new Point(0, 0)), distance);
    }

    private static GeoResult<GeoLocation<String>> geoResult(String name, Distance distance) {
        return new GeoResult<>(new GeoLocation<>(name, new Point(0, 0)), distance);
    }
}
