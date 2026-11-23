package com.enruta.tracking.ws;

import com.enruta.tracking.service.TrackingBroadcaster;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Canal en vivo del mapa.
 *
 * <p>El token viaja en el query string porque el API de WebSocket del navegador no permite
 * cabeceras propias (ADR-0010). En produccion se cambiaria por un ticket efimero de un solo uso.
 */
@Component
public class TrackingWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TrackingWebSocketHandler.class);
    private static final String SHIPMENT_ATTR = "shipmentId";

    private final JwtDecoder jwtDecoder;
    private final TrackingBroadcaster broadcaster;
    private final StringRedisTemplate redis;

    public TrackingWebSocketHandler(
            JwtDecoder jwtDecoder, TrackingBroadcaster broadcaster, StringRedisTemplate redis) {
        this.jwtDecoder = jwtDecoder;
        this.broadcaster = broadcaster;
        this.redis = redis;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, String> query = queryParams(session.getUri());
        String shipmentId = query.get("shipmentId");
        String token = query.get("token");

        if (shipmentId == null || token == null) {
            session.close(CloseStatus.BAD_DATA.withReason("shipmentId y token son obligatorios"));
            return;
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            session.getAttributes().put("subject", jwt.getSubject());
        } catch (Exception invalidToken) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("token invalido"));
            return;
        }

        session.getAttributes().put(SHIPMENT_ATTR, shipmentId);
        broadcaster.subscribe(shipmentId, session);
        session.sendMessage(
                new TextMessage("{\"type\":\"subscribed\",\"shipmentId\":\"" + shipmentId + "\"}"));
        sendSnapshot(session, shipmentId);
        log.info(
                "ws suscrito shipment={} sesiones={}",
                shipmentId,
                broadcaster.subscribers(shipmentId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        if ("ping".equalsIgnoreCase(message.getPayload())) {
            session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object shipmentId = session.getAttributes().get(SHIPMENT_ATTR);
        if (shipmentId != null) {
            broadcaster.unsubscribe(shipmentId.toString(), session);
        }
        broadcaster.removeSession(session);
    }

    /**
     * Al reconectar, el cliente recibe la ultima posicion conocida sin esperar al siguiente ping.
     */
    private void sendSnapshot(WebSocketSession session, String shipmentId) throws Exception {
        Object driverId = redis.opsForHash().get("shipment:" + shipmentId, "driverId");
        if (driverId == null) {
            return;
        }
        Map<Object, Object> last = redis.opsForHash().entries("driver:" + driverId + ":last");
        if (last.isEmpty()) {
            return;
        }
        String json =
                "{\"type\":\"position\",\"shipmentId\":\""
                        + shipmentId
                        + "\",\"driverId\":\""
                        + driverId
                        + "\",\"lat\":"
                        + last.get("lat")
                        + ",\"lng\":"
                        + last.get("lng")
                        + ",\"at\":\""
                        + last.get("at")
                        + "\",\"snapshot\":true}";
        session.sendMessage(new TextMessage(json));
    }

    static Map<String, String> queryParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        if (uri == null || uri.getQuery() == null) {
            return params;
        }
        for (String pair : uri.getQuery().split("&")) {
            int index = pair.indexOf('=');
            if (index > 0) {
                params.put(pair.substring(0, index), pair.substring(index + 1));
            }
        }
        return params;
    }
}
