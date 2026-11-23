package com.enruta.dispatch.service;

import java.util.List;
import java.util.UUID;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.stereotype.Component;

/**
 * Indice geografico de conductores disponibles.
 *
 * <p>Redis GEO es la fuente para "quien esta cerca": consultas O(log N) sobre un sorted set.
 * PostgreSQL guarda el perfil; Redis solo la posicion efimera (se puede reconstruir).
 */
@Component
public class CandidateFinder {

    public static final String AVAILABLE_KEY = "drivers:available";

    public record Candidate(UUID driverId, int distanceMeters) {}

    private final StringRedisTemplate redis;

    public CandidateFinder(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public List<Candidate> nearest(double lat, double lng, int radiusMeters, int limit) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                redis.opsForGeo()
                        .search(
                                AVAILABLE_KEY,
                                GeoReference.fromCoordinate(new Point(lng, lat)),
                                new Distance(radiusMeters, Metrics.METERS),
                                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                        .includeDistance()
                                        .sortAscending()
                                        .limit(limit));
        if (results == null) {
            return List.of();
        }
        return results.getContent().stream()
                .map(this::toCandidate)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Candidate toCandidate(GeoResult<RedisGeoCommands.GeoLocation<String>> result) {
        try {
            UUID driverId = UUID.fromString(result.getContent().getName());
            int meters = result.getDistance() == null ? 0 : (int) result.getDistance().getValue();
            return new Candidate(driverId, meters);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    public void index(UUID driverId, double lat, double lng) {
        redis.opsForGeo().add(AVAILABLE_KEY, new Point(lng, lat), driverId.toString());
    }

    public void unindex(UUID driverId) {
        redis.opsForGeo().remove(AVAILABLE_KEY, driverId.toString());
    }

    public void clear() {
        redis.delete(AVAILABLE_KEY);
    }
}
