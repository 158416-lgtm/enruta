package com.enruta.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Auth solo mapea sus propias entidades: las de {@code common} (outbox, idempotencia) son opt-in
 * por servicio para que ningun servicio dependa de tablas que no le pertenecen.
 */
@SpringBootApplication(scanBasePackages = "com.enruta")
@EntityScan("com.enruta.auth")
@EnableJpaRepositories("com.enruta.auth")
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
