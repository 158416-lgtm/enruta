package com.enruta.shipment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ShipmentStatusTest {

    @Test
    void happyPathIsAllowed() {
        assertThat(ShipmentStatus.CREATED.canTransitionTo(ShipmentStatus.ASSIGNED)).isTrue();
        assertThat(ShipmentStatus.ASSIGNED.canTransitionTo(ShipmentStatus.PICKED_UP)).isTrue();
        assertThat(ShipmentStatus.PICKED_UP.canTransitionTo(ShipmentStatus.IN_TRANSIT)).isTrue();
        assertThat(ShipmentStatus.IN_TRANSIT.canTransitionTo(ShipmentStatus.DELIVERED)).isTrue();
    }

    @Test
    void cannotSkipSteps() {
        assertThat(ShipmentStatus.CREATED.canTransitionTo(ShipmentStatus.DELIVERED)).isFalse();
        assertThat(ShipmentStatus.CREATED.canTransitionTo(ShipmentStatus.PICKED_UP)).isFalse();
        assertThat(ShipmentStatus.ASSIGNED.canTransitionTo(ShipmentStatus.DELIVERED)).isFalse();
    }

    @Test
    void compensationReturnsToCreated() {
        assertThat(ShipmentStatus.ASSIGNED.canTransitionTo(ShipmentStatus.CREATED)).isTrue();
    }

    @Test
    void terminalStatesAcceptNothing() {
        for (ShipmentStatus terminal :
                new ShipmentStatus[] {
                    ShipmentStatus.DELIVERED, ShipmentStatus.FAILED, ShipmentStatus.CANCELLED
                }) {
            for (ShipmentStatus candidate : ShipmentStatus.values()) {
                assertThat(terminal.canTransitionTo(candidate))
                        .as("%s -> %s", terminal, candidate)
                        .isFalse();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ShipmentStatus.class)
    void everyStatusHasExplicitRules(ShipmentStatus status) {
        // Si alguien agrega un estado sin reglas, este test lo obliga a decidir la matriz.
        long allowed =
                java.util.Arrays.stream(ShipmentStatus.values())
                        .filter(status::canTransitionTo)
                        .count();
        if (status.isTerminal()) {
            assertThat(allowed).isZero();
        } else {
            assertThat(allowed).isPositive();
        }
    }

    @Test
    void customerCanCancelUntilPickupOnly() {
        assertThat(ShipmentStatus.CREATED.isCancellableByCustomer()).isTrue();
        assertThat(ShipmentStatus.ASSIGNED.isCancellableByCustomer()).isTrue();
        assertThat(ShipmentStatus.PICKED_UP.isCancellableByCustomer()).isFalse();
        assertThat(ShipmentStatus.IN_TRANSIT.isCancellableByCustomer()).isFalse();
    }
}
