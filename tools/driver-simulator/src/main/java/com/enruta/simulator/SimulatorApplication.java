package com.enruta.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Simulador de conductores para la demo.
 *
 * <p>No es un servicio de negocio: emite pings GPS por calles reales de Fusagasuga contra {@code
 * tracking-service} para que el mapa tenga vida durante la exposicion.
 */
@SpringBootApplication(scanBasePackages = "com.enruta")
@ConfigurationPropertiesScan
public class SimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulatorApplication.class, args);
    }
}
