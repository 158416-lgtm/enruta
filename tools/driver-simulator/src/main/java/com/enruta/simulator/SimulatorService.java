package com.enruta.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Mueve los conductores por calles reales y acepta ofertas con un retardo humano.
 *
 * <p>Sin esto la demo no se ve: es lo que hace que el mapa tenga movimiento y que la asignacion
 * ocurra sola en pocos segundos.
 */
@Service
public class SimulatorService {

    private static final Logger log = LoggerFactory.getLogger(SimulatorService.class);
    private static final UUID[] DRIVER_IDS = {
        UUID.fromString("11111111-1111-1111-1111-111111111101"),
        UUID.fromString("11111111-1111-1111-1111-111111111102"),
        UUID.fromString("11111111-1111-1111-1111-111111111103"),
        UUID.fromString("11111111-1111-1111-1111-111111111104"),
        UUID.fromString("11111111-1111-1111-1111-111111111105"),
        UUID.fromString("11111111-1111-1111-1111-111111111106")
    };

    private final SimulatorClient client;
    private final SimulatorProperties properties;
    private final List<DriverSession> sessions = new ArrayList<>();
    private boolean ready;

    public SimulatorService(SimulatorClient client, SimulatorProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        int count = Math.min(properties.driverCount(), DRIVER_IDS.length);
        for (int i = 0; i < count; i++) {
            DriverSession session =
                    new DriverSession(DRIVER_IDS[i], "driver" + (i + 1) + "@enruta.test");
            try {
                session.token = client.login(session.email);
                session.route.addAll(routeFor(i));
                sessions.add(session);
                log.info(
                        "conductor simulado listo id={} puntos={}",
                        session.driverId,
                        session.route.size());
            } catch (Exception ex) {
                log.error("no se pudo inicializar driver{}: {}", i + 1, ex.getMessage());
            }
        }
        ready = !sessions.isEmpty();
        if (!ready) {
            log.error("simulador sin conductores: revisa auth y dispatch");
        }
    }

    @Scheduled(
            fixedDelayString = "${enruta.simulator.tick-interval-ms:2000}",
            initialDelayString = "${enruta.simulator.tick-initial-delay-ms:5000}")
    public void tick() {
        if (!ready) {
            return;
        }
        for (DriverSession session : sessions) {
            session.advance(session.direction > 0 ? 1 : -1);
            double[] position = session.current();
            client.postLocation(session.token, session.driverId, position[0], position[1]);
            handleOffers(session);
        }
    }

    private void handleOffers(DriverSession session) {
        if (!properties.autoAccept()) {
            return;
        }
        if (session.pendingOfferId != null) {
            if (session.acceptDelayTicks-- <= 0) {
                if (client.accept(session.token, session.pendingOfferId)) {
                    log.info(
                            "conductor {} acepto oferta {}", session.email, session.pendingOfferId);
                }
                session.pendingOfferId = null;
            }
            return;
        }
        List<JsonNode> offers = client.pendingOffers(session.token, session.driverId);
        if (!offers.isEmpty()) {
            JsonNode offer = offers.getFirst();
            session.pendingOfferId = UUID.fromString(offer.get("id").asText());
            session.acceptDelayTicks = 1 + (int) (Math.random() * 2);
            log.info(
                    "conductor {} recibio oferta {} ({} m)",
                    session.email,
                    session.pendingOfferId,
                    offer.get("distanceMeters").asInt());
        }
    }

    /** Cada conductor recorre un tramo distinto alrededor del centro de Fusagasuga. */
    private List<double[]> routeFor(int index) {
        double lat = properties.centerLat();
        double lng = properties.centerLng();
        double[][] waypoints = {
            {lat - 0.0010, lng - 0.0005, lat - 0.0075, lng + 0.0074},
            {lat - 0.0049, lng - 0.0054, lat + 0.0071, lng + 0.0074},
            {lat + 0.0031, lng - 0.0026, lat - 0.0099, lng + 0.0024},
            {lat - 0.0099, lng + 0.0024, lat - 0.0010, lng - 0.0005},
            {lat + 0.0071, lng + 0.0074, lat - 0.0049, lng - 0.0054},
            {lat - 0.0069, lng + 0.0046, lat + 0.0031, lng - 0.0026}
        };
        double[] w = waypoints[index % waypoints.length];
        return client.route(w[0], w[1], w[2], w[3]);
    }
}
