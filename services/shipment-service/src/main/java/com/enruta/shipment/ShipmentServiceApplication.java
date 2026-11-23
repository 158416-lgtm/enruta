package com.enruta.shipment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Mapea sus entidades y las de common (outbox/idempotencia) porque las usa de verdad. */
@SpringBootApplication(scanBasePackages = "com.enruta")
@EntityScan({"com.enruta.shipment", "com.enruta.common"})
@EnableJpaRepositories({"com.enruta.shipment", "com.enruta.common"})
public class ShipmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShipmentServiceApplication.class, args);
    }
}
