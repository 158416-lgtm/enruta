package com.enruta.shipment.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Estados y transiciones permitidas del envio. Cualquier otra transicion es 409. */
public enum ShipmentStatus {
    CREATED,
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    FAILED,
    CANCELLED,
    NO_DRIVER;

    private static final Map<ShipmentStatus, Set<ShipmentStatus>> ALLOWED =
            Map.of(
                    CREATED, EnumSet.of(ASSIGNED, CANCELLED, NO_DRIVER),
                    NO_DRIVER, EnumSet.of(ASSIGNED, CANCELLED),
                    ASSIGNED, EnumSet.of(PICKED_UP, CREATED, FAILED, CANCELLED),
                    PICKED_UP, EnumSet.of(IN_TRANSIT, FAILED, CANCELLED),
                    IN_TRANSIT, EnumSet.of(DELIVERED, FAILED, CANCELLED),
                    DELIVERED, EnumSet.noneOf(ShipmentStatus.class),
                    FAILED, EnumSet.noneOf(ShipmentStatus.class),
                    CANCELLED, EnumSet.noneOf(ShipmentStatus.class));

    public boolean canTransitionTo(ShipmentStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    /** Estados en los que el cliente aun puede cancelar su envio. */
    public boolean isCancellableByCustomer() {
        return this == CREATED || this == NO_DRIVER || this == ASSIGNED;
    }
}
