package com.enruta.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "enruta.simulator")
public record SimulatorProperties(
        String authUrl,
        String dispatchUrl,
        String osrmUrl,
        int driverCount,
        double centerLat,
        double centerLng,
        String password,
        boolean autoAccept) {}
