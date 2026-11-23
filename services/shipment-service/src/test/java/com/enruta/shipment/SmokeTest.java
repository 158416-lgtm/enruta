package com.enruta.shipment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class SmokeTest {

    @Test
    void applicationIsAnnotatedAsSpringBootApp() {
        assertThat(ShipmentServiceApplication.class.getAnnotation(SpringBootApplication.class))
                .isNotNull();
    }
}
