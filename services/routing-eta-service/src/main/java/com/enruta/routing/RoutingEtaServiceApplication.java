package com.enruta.routing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.enruta")
public class RoutingEtaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoutingEtaServiceApplication.class, args);
    }
}
