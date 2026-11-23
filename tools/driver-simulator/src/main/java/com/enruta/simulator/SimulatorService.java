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
 * Ciclo de vida de un conductor simulado.
 *
 * <pre>
 * PATROL → (acepta oferta) → TO_PICKUP → DWELL_PICKUP (3 pings) → TO_DROPOFF → DONE → PATROL
 * </pre>
 *
 * <p>El dwell de 3 pings existe porque la geocerca exige histeresis: sin el, el conductor pasaria
 * por el punto y el envio nunca cambiaria de estado.
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
    private static final int DWELL_TICKS = 3;

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
                session.resetRoute(patrolRoute(i));
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
            log.error("simulador sin conductores: revisa auth, shipment y dispatch");
        }
    }

    @Scheduled(
            fixedDelayString = "${enruta.simulator.tick-interval-ms:1000}",
            initialDelayString = "${enruta.simulator.tick-initial-delay-ms:5000}")
    public void tick() {
        if (!ready) {
            return;
        }
        for (DriverSession session : sessions) {
            try {
                step(session);
            } catch (Exception ex) {
                log.warn("tick fallido driver={}: {}", session.driverId, ex.getMessage());
            }
        }
    }

    private void step(DriverSession session) {
        if (session.mode == DriverSession.Mode.PATROL && session.pendingShipmentId == null) {
            askForOffers(session);
        }
        if (session.pendingShipmentId != null) {
            startTrip(session);
            if (session.mode == DriverSession.Mode.PATROL) {
                reportPosition(session);
                return;
            }
        }

        switch (session.mode) {
            case PATROL -> {
                session.advance();
                if (session.atEnd()) {
                    session.cursor = 0;
                }
                reportPosition(session);
            }
            case TO_PICKUP -> {
                session.advance();
                reportPosition(session);
                if (session.atEnd()) {
                    session.mode = DriverSession.Mode.DWELL_PICKUP;
                    session.dwellTicks = DWELL_TICKS;
                    log.info("conductor {} llego al origen", session.email);
                }
            }
            case DWELL_PICKUP -> {
                reportPosition(session);
                if (--session.dwellTicks <= 0) {
                    startDropoffLeg(session);
                }
            }
            case TO_DROPOFF -> {
                session.advance();
                reportPosition(session);
                if (session.atEnd()) {
                    session.mode = DriverSession.Mode.DONE;
                    session.doneTicks = 0;
                    log.info("conductor {} llego al destino", session.email);
                }
            }
            case DONE -> {
                reportPosition(session);
                if (++session.doneTicks > DWELL_TICKS) {
                    log.info("conductor {} libre de nuevo", session.email);
                    session.clearAssignment();
                    session.resetRoute(patrolRoute(session.driverId.hashCode() & 0x7fffffff));
                }
            }
        }
    }

    private void askForOffers(DriverSession session) {
        if (!properties.autoAccept()) {
            return;
        }
        List<JsonNode> offers = client.pendingOffers(session.token, session.driverId);
        if (offers.isEmpty()) {
            return;
        }
        JsonNode offer = offers.getFirst();
        UUID offerId = UUID.fromString(offer.get("id").asText());
        if (session.acceptDelayTicks > 0) {
            session.acceptDelayTicks--;
            return;
        }
        if (client.accept(session.token, offerId)) {
            session.pendingShipmentId = UUID.fromString(offer.get("shipmentId").asText());
            session.fetchRetries = 0;
            log.info(
                    "conductor {} acepto oferta {} del envio {}",
                    session.email,
                    offerId,
                    session.pendingShipmentId);
        } else {
            session.acceptDelayTicks = 1;
        }
    }

    /** La asignacion es asincrona: se reintenta hasta que shipment refleje el conductor. */
    private void startTrip(DriverSession session) {
        SimulatorClient.ShipmentGeometry geometry =
                client.shipment(session.token, session.pendingShipmentId);
        if (geometry == null) {
            session.fetchRetries++;
            if (session.fetchRetries > 15) {
                log.warn("no se pudo leer el envio {}: se libera", session.pendingShipmentId);
                session.clearAssignment();
            }
            return;
        }
        double[] position = session.current();
        session.shipmentId = session.pendingShipmentId;
        session.pendingShipmentId = null;
        session.dropoffLat = geometry.dropoffLat();
        session.dropoffLng = geometry.dropoffLng();
        session.resetRoute(
                client.route(position[0], position[1], geometry.pickupLat(), geometry.pickupLng()));
        session.mode = DriverSession.Mode.TO_PICKUP;
        log.info("conductor {} va al origen del envio {}", session.email, session.shipmentId);
    }

    private void startDropoffLeg(DriverSession session) {
        double[] position = session.current();
        session.resetRoute(
                client.route(position[0], position[1], session.dropoffLat, session.dropoffLng));
        session.mode = DriverSession.Mode.TO_DROPOFF;
    }

    private void reportPosition(DriverSession session) {
        double[] position = session.current();
        client.postLocation(session.token, session.driverId, position[0], position[1]);
    }

    private List<double[]> patrolRoute(int index) {
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
