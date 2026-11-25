package com.enruta.simulator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimulatorServiceTest {

    @Mock private SimulatorClient client;

    private SimulatorService service;

    @BeforeEach
    void setUp() {
        SimulatorProperties properties =
                new SimulatorProperties(
                        "http://auth",
                        "http://dispatch",
                        "http://shipment",
                        "http://tracking",
                        "http://osrm",
                        6,
                        4.3449,
                        -74.3674,
                        "Driver1234!",
                        true);
        service = new SimulatorService(client, properties);
    }

    @Test
    void initLogsInEveryDriverAndLoadsARoute() {
        when(client.login(anyString())).thenReturn("token");
        when(client.route(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new double[] {4.34, -74.36}, new double[] {4.35, -74.37}));

        service.init();

        verify(client, atLeast(6)).login(anyString());
        verify(client, atLeast(6)).route(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void aTickReportsAPositionForEachDriver() {
        when(client.login(anyString())).thenReturn("token");
        when(client.route(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new double[] {4.34, -74.36}, new double[] {4.35, -74.37}));
        when(client.pendingOffers(anyString(), any())).thenReturn(List.of());
        service.init();

        service.tick();

        verify(client, atLeast(6))
                .postLocation(anyString(), any(UUID.class), anyDouble(), anyDouble());
    }

    @Test
    void aTickWithoutDriversDoesNothing() {
        service.tick(); // no init previo: ready = false

        verify(client, org.mockito.Mockito.never())
                .postLocation(any(), any(), anyDouble(), anyDouble());
    }
}
