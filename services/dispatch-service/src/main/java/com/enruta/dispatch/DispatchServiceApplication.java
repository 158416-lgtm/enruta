package com.enruta.dispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.enruta")
@EntityScan({"com.enruta.dispatch", "com.enruta.common.outbox"})
@EnableJpaRepositories({"com.enruta.dispatch", "com.enruta.common.outbox"})
@EnableScheduling
public class DispatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchServiceApplication.class, args);
    }
}
