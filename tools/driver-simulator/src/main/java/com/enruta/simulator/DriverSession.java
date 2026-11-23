package com.enruta.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Estado en memoria de un conductor simulado. */
class DriverSession {

    enum Mode {
        PATROL,
        TO_PICKUP,
        DWELL_PICKUP,
        TO_DROPOFF,
        DONE
    }

    final UUID driverId;
    final String email;
    final List<double[]> route = new ArrayList<>();
    String token;
    int cursor;
    int step = 1;
    Mode mode = Mode.PATROL;
    int dwellTicks;
    int doneTicks;
    int fetchRetries;

    UUID shipmentId;
    UUID pendingShipmentId;
    double dropoffLat;
    double dropoffLng;

    int acceptDelayTicks;

    DriverSession(UUID driverId, String email) {
        this.driverId = driverId;
        this.email = email;
    }

    double[] current() {
        if (route.isEmpty()) {
            return new double[] {0, 0};
        }
        return route.get(Math.min(cursor, route.size() - 1));
    }

    boolean atEnd() {
        return cursor >= route.size() - 1;
    }

    void resetRoute(List<double[]> points) {
        route.clear();
        route.addAll(points);
        cursor = 0;
        // ~20 ticks por tramo: una demo completa (origen + destino) en ~50 s.
        step = Math.max(1, route.size() / 20);
    }

    void advance() {
        cursor = Math.min(cursor + step, route.size() - 1);
    }

    void clearAssignment() {
        shipmentId = null;
        pendingShipmentId = null;
        mode = Mode.PATROL;
        doneTicks = 0;
        fetchRetries = 0;
    }
}
