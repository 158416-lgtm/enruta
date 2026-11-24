package com.enruta.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.enruta.notification.domain.Notification;
import com.enruta.notification.service.NotificationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Prueba real de entrega: MailHog como servidor SMTP y su API HTTP para verificar que el correo
 * llego de verdad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class NotificationMailTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("notifdb");

    @Container
    static final GenericContainer<?> MAILHOG =
            new GenericContainer<>(DockerImageName.parse("mailhog/mailhog:v1.0.1"))
                    .withExposedPorts(1025, 8025);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.mail.host", MAILHOG::getHost);
        registry.add("spring.mail.port", () -> MAILHOG.getMappedPort(1025));
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("enruta.notification.retry-initial-delay-ms", () -> "3600000");
    }

    @Autowired private NotificationService notifications;

    @Test
    void deliversTheEmailToTheSmtpServer() {
        String recipient =
                "verificacion-" + UUID.randomUUID().toString().substring(0, 8) + "@enruta.test";

        Notification notification =
                notifications.createAndAttempt(
                        UUID.randomUUID(),
                        "EMAIL",
                        recipient,
                        "SHIPMENT_DELIVERED",
                        "{\"shipmentId\":\"abc\",\"dropoffAddress\":\"Terminal de Transportes\"}");

        assertThat(notification.getStatus()).isEqualTo(Notification.Status.SENT);

        String mailhogApi =
                "http://"
                        + MAILHOG.getHost()
                        + ":"
                        + MAILHOG.getMappedPort(8025)
                        + "/api/v2/messages";
        RestClient client = RestClient.create();

        String body = "";
        for (int i = 0; i < 20; i++) {
            body = client.get().uri(mailhogApi).retrieve().body(String.class);
            if (body != null && body.contains(recipient)) {
                break;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        assertThat(body).contains(recipient).contains("Tu envio fue entregado");
    }
}
