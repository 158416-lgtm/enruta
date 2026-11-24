package com.enruta.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.notification.domain.Notification;
import com.enruta.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class NotificationServiceTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final EmailSender sender = mock(EmailSender.class);
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

    private NotificationService service(RetryPolicy policy) {
        return new NotificationService(
                repository,
                sender,
                policy,
                kafka,
                new ObjectMapper(),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    private void stubSave() {
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void successfulSendMarksNotificationAsSent() throws Exception {
        stubSave();
        NotificationService service = service(new RetryPolicy(3, List.of(Duration.ofSeconds(2))));

        Notification notification =
                service.createAndAttempt(
                        UUID.randomUUID(), "EMAIL", "cliente@enruta.test", "TEST", "{}");

        assertThat(notification.getStatus()).isEqualTo(Notification.Status.SENT);
        assertThat(notification.getAttempts()).isEqualTo((short) 1);
        assertThat(notification.getSentAt()).isNotNull();
        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void failedSendSchedulesARetryInsteadOfGivingUp() throws Exception {
        stubSave();
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp caido"))
                .when(sender)
                .send(any());
        NotificationService service =
                service(new RetryPolicy(3, List.of(Duration.ofSeconds(2), Duration.ofSeconds(10))));

        Notification notification =
                service.createAndAttempt(
                        UUID.randomUUID(), "EMAIL", "cliente@enruta.test", "TEST", "{}");

        assertThat(notification.getStatus()).isEqualTo(Notification.Status.PENDING);
        assertThat(notification.getAttempts()).isEqualTo((short) 1);
        assertThat(notification.getNextAttemptAt()).isNotNull();
        assertThat(notification.getLastError()).contains("smtp caido");
        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void exhaustionMovesTheNotificationToTheDeadLetterTopic() throws Exception {
        stubSave();
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp caido"))
                .when(sender)
                .send(any());
        NotificationService service = service(new RetryPolicy(1, List.of(Duration.ofSeconds(2))));

        Notification notification =
                service.createAndAttempt(
                        UUID.randomUUID(),
                        "EMAIL",
                        "cliente@enruta.test",
                        "SHIPMENT_DELIVERED",
                        "{}");

        assertThat(notification.getStatus()).isEqualTo(Notification.Status.FAILED);
        assertThat(notification.getNextAttemptAt()).isNull();
        verify(kafka).send(eq("notification.commands.dlq"), anyString(), anyString());
    }
}
