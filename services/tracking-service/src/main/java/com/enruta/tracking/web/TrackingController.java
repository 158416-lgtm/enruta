package com.enruta.tracking.web;

import com.enruta.common.geo.GeoMath;
import com.enruta.tracking.service.EtaClient;
import com.enruta.tracking.service.TrackingBroadcaster;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Estado en vivo que consume el mapa al cargar (antes de abrir el WebSocket). */
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final StringRedisTemplate redis;
    private final TrackingBroadcaster broadcaster;
    private final EtaClient etaClient;

    public TrackingController(
            StringRedisTemplate redis, TrackingBroadcaster broadcaster, EtaClient etaClient) {
        this.redis = redis;
        this.broadcaster = broadcaster;
        this.etaClient = etaClient;
    }

    @GetMapping("/shipments/{shipmentId}")
    public Map<String, Object> state(
            @PathVariable UUID shipmentId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String id = shipmentId.toString();
        Map<Object, Object> shipment = redis.opsForHash().entries("shipment:" + id);
        Map<String, Object> response = new HashMap<>();
        response.put("shipmentId", id);
        response.put("subscribers", broadcaster.subscribers(id));
        response.put("shipment", shipment);

        Object driverId = shipment.get("driverId");
        if (driverId == null) {
            return response;
        }
        Map<Object, Object> last = redis.opsForHash().entries("driver:" + driverId + ":last");
        if (last.isEmpty()) {
            return response;
        }
        response.put("lastPosition", last);

        double lat = Double.parseDouble(last.get("lat").toString());
        double lng = Double.parseDouble(last.get("lng").toString());
        if (shipment.containsKey("dropoffLat") && shipment.containsKey("dropoffLng")) {
            double dropLat = Double.parseDouble(shipment.get("dropoffLat").toString());
            double dropLng = Double.parseDouble(shipment.get("dropoffLng").toString());
            response.put(
                    "distanceToDropoffMeters",
                    Math.round(GeoMath.distanceMeters(lat, lng, dropLat, dropLng)));
            Long eta = etaClient.etaSeconds(lat, lng, dropLat, dropLng, authorization);
            if (eta != null) {
                response.put("etaSeconds", eta);
            }
        }
        return response;
    }
}
