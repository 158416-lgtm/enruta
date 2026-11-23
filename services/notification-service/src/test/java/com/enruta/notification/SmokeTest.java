package com.enruta.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class SmokeTest {

    @Test
    void applicationIsAnnotatedAsSpringBootApp() {
        assertThat(NotificationServiceApplication.class.getAnnotation(SpringBootApplication.class))
                .isNotNull();
    }
}
