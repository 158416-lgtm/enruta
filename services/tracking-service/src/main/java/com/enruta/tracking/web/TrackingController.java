package com.enruta.tracking.web;

import com.enruta.common.error.BusinessException;
import com.enruta.common.geo.GeoMath;
import com.enruta.tracking.service.EtaClient;
import com.enruta.tracking.service.TrackingBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Estado en vivo y recorrido que consume el mapa (antes y despues del WebSocket). */
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final StringRedisTemplate redis;
    private final TrackingBroadcaster broadcaster;
    private final EtaClient etaClient;
    private final ObjectMapper objectMapper;

    public TrackingController(
            StringRedisTemplate redis,
            TrackingBroadcaster broadcaster,
            EtaClient etaClient,
            ObjectMapper objectMapper) {
        this.redis = redis;
        this.broadcaster = broadcaster;
        this.etaClient = etaClient;
        this.objectMapper = objectMapper;
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

    /** Ultimos 200 puntos del envio, del mas reciente al mas antiguo. */
    @GetMapping("/shipments/{shipmentId}/trail")
    public Map<String, Object> trail(@PathVariable UUID shipmentId) {
        String id = shipmentId.toString();
        List<String> raw = redis.opsForList().range("shipment:" + id + ":trail", 0, 199);
        List<Map<String, Object>> points = new ArrayList<>();
        if (raw != null) {
            for (String item : raw) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> point = objectMapper.readValue(item, Map.class);
                    points.add(point);
                } catch (Exception ex) {
                    // Un punto corrupto no debe romper el recorrido completo.
                }
            }
        }
        Map<String, Object> response = new HashMap<>();
        response.put("shipmentId", id);
        response.put("points", points);
        response.put("count", points.size());
        return response;
    }

    /** Ultima posicion conocida de un conductor, sin necesidad de conocer su envio. */
    @GetMapping("/drivers/{driverId}/last")
    public Map<String, Object> lastPosition(@PathVariable UUID driverId) {
        Map<Object, Object> last = redis.opsForHash().entries("driver:" + driverId + ":last");
        if (last.isEmpty()) {
            throw BusinessException.notFound(
                    "POSITION_NOT_FOUND", "El conductor aun no ha enviado posiciones");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("driverId", driverId.toString());
        response.put("lat", Double.parseDouble(last.get("lat").toString()));
        response.put("lng", Double.parseDouble(last.get("lng").toString()));
        response.put("at", last.get("at"));
        return response;
    }

    /** Indicadores operativos del rastreo: envios con recorrido y conductores con posicion. */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Set<String> trails = redis.keys("shipment:*:trail");
        Set<String> drivers = redis.keys("driver:*:last");
        Map<String, Object> response = new HashMap<>();
        response.put("shipmentsWithTrail", trails == null ? 0 : trails.size());
        response.put("driversWithPosition", drivers == null ? 0 : drivers.size());
        return response;
    }
}
