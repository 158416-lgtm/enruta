package com.enruta.tracking.service;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Geocerca con histeresis.
 *
 * <p>Un solo ping dentro del radio no basta: hacen falta {@code REQUIRED_HITS} consecutivos. Sin
 * esto el estado parpadearia en el mapa (un GPS urbano oscila +-30 m) y el envio cambiaria de
 * estado repetidamente.
 */
@Service
public class GeofenceService {

    static final int REQUIRED_HITS = 3;
    private static final Duration WINDOW = Duration.ofMinutes(2);

    private final StringRedisTemplate redis;

    public GeofenceService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @return true la primera vez que se confirma la entrada a la geocerca.
     */
    public boolean registerHit(String shipmentId, String fence, boolean inside) {
        String hitsKey = "geo:" + shipmentId + ":" + fence + ":hits";
        String doneKey = "geo:" + shipmentId + ":" + fence + ":done";

        if (!inside) {
            redis.delete(hitsKey);
            return false;
        }
        if (Boolean.TRUE.equals(redis.hasKey(doneKey))) {
            return false;
        }
        Long hits = redis.opsForValue().increment(hitsKey);
        redis.expire(hitsKey, WINDOW);
        if (hits != null && hits >= REQUIRED_HITS) {
            redis.opsForValue().set(doneKey, "1", Duration.ofHours(6));
            return true;
        }
        return false;
    }

    public boolean alreadyConfirmed(String shipmentId, String fence) {
        return Boolean.TRUE.equals(redis.hasKey("geo:" + shipmentId + ":" + fence + ":done"));
    }
}
