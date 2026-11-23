package com.enruta.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class SmokeTest {

    @Test
    void applicationIsAnnotatedAsSpringBootApp() {
        assertThat(DispatchServiceApplication.class.getAnnotation(SpringBootApplication.class))
                .isNotNull();
    }
}
