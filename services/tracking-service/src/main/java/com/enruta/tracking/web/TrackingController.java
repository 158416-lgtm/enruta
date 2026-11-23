package com.enruta.tracking.web;

import com.enruta.tracking.service.TrackingBroadcaster;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Estado en vivo que consume el mapa al cargar (antes de abrir el WebSocket). */
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final StringRedisTemplate redis;
    private final TrackingBroadcaster broadcaster;

    public TrackingController(StringRedisTemplate redis, TrackingBroadcaster broadcaster) {
        this.redis = redis;
        this.broadcaster = broadcaster;
    }

    @GetMapping("/shipments/{shipmentId}")
    public Map<String, Object> state(@PathVariable UUID shipmentId) {
        String id = shipmentId.toString();
        Map<Object, Object> shipment = redis.opsForHash().entries("shipment:" + id);
        Map<String, Object> response = new HashMap<>();
        response.put("shipmentId", id);
        response.put("subscribers", broadcaster.subscribers(id));
        response.put("shipment", shipment);

        Object driverId = shipment.get("driverId");
        if (driverId != null) {
            Map<Object, Object> last = redis.opsForHash().entries("driver:" + driverId + ":last");
            if (!last.isEmpty()) {
                response.put("lastPosition", last);
            }
        }
        return response;
    }
}
