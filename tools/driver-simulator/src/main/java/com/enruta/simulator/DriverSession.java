package com.enruta.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Estado en memoria de un conductor simulado. */
class DriverSession {

    final UUID driverId;
    final String email;
    final List<double[]> route = new ArrayList<>();
    String token;
    int cursor;
    int direction = 1;
    int acceptDelayTicks;
    UUID pendingOfferId;

    DriverSession(UUID driverId, String email) {
        this.driverId = driverId;
        this.email = email;
    }

    double[] current() {
        return route.get(Math.min(cursor, route.size() - 1));
    }

    /** Avanza a lo largo de la ruta y rebota en los extremos para no quedarse parado. */
    void advance(int step) {
        if (route.size() < 2) {
            return;
        }
        cursor += step * direction;
        if (cursor >= route.size()) {
            cursor = route.size() - 2;
            direction = -1;
        } else if (cursor < 0) {
            cursor = 1;
            direction = 1;
        }
    }
}
