package com.enruta.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Registro de suscriptores por envio.
 *
 * <p>Una instancia mantiene sus propias conexiones (ADR-0010). Con varias replicas habria que
 * repartir por Redis Pub/Sub; se documenta como evolucion consciente.
 */
@Component
public class TrackingBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(TrackingBroadcaster.class);

    private final Map<String, Set<WebSocketSession>> sessionsByShipment = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public TrackingBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void subscribe(String shipmentId, WebSocketSession session) {
        sessionsByShipment
                .computeIfAbsent(shipmentId, key -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    public void unsubscribe(String shipmentId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByShipment.get(shipmentId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByShipment.remove(shipmentId);
            }
        }
    }

    public void removeSession(WebSocketSession session) {
        sessionsByShipment.values().forEach(sessions -> sessions.remove(session));
    }

    public int subscribers(String shipmentId) {
        return sessionsByShipment.getOrDefault(shipmentId, Set.of()).size();
    }

    public void position(String shipmentId, PositionService.Position position) {
        send(
                shipmentId,
                Map.of(
                        "type", "position",
                        "shipmentId", shipmentId,
                        "driverId", position.driverId().toString(),
                        "lat", position.lat(),
                        "lng", position.lng(),
                        "at", position.recordedAt().toString()));
    }

    public void status(String shipmentId, String status) {
        send(shipmentId, Map.of("type", "status", "shipmentId", shipmentId, "status", status));
    }

    private void send(String shipmentId, Map<String, Object> payload) {
        Set<WebSocketSession> sessions = sessionsByShipment.get(shipmentId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.warn("no se pudo serializar mensaje ws", ex);
            return;
        }
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            } catch (Exception ex) {
                log.debug("fallo al enviar ws session={}: {}", session.getId(), ex.getMessage());
                removeSession(session);
            }
        }
    }
}
