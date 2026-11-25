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

    @Test
    void processDueAttemptsEveryReservedNotification() throws Exception {
        stubSave();
        Notification pending =
                new Notification(UUID.randomUUID(), "EMAIL", "cliente@enruta.test", "TEST", "{}");
        when(repository.lockDue(50)).thenReturn(List.of(pending));
        NotificationService service = service(new RetryPolicy(3, List.of(Duration.ofSeconds(2))));

        int processed = service.processDue();

        assertThat(processed).isEqualTo(1);
        assertThat(pending.getStatus()).isEqualTo(Notification.Status.SENT);
        verify(sender).send(pending);
    }

    @Test
    void attemptNowOnlyRetriesPendingNotifications() throws Exception {
        stubSave();
        Notification pending =
                new Notification(UUID.randomUUID(), "EMAIL", "cliente@enruta.test", "TEST", "{}");
        when(repository.findById(pending.getId())).thenReturn(java.util.Optional.of(pending));
        NotificationService service = service(new RetryPolicy(3, List.of(Duration.ofSeconds(2))));

        Notification result = service.attemptNow(pending.getId());

        assertThat(result.getStatus()).isEqualTo(Notification.Status.SENT);
        verify(sender).send(pending);
    }

    @Test
    void attemptNowLeavesSentNotificationsUntouched() throws Exception {
        stubSave();
        Notification sent =
                new Notification(UUID.randomUUID(), "EMAIL", "cliente@enruta.test", "TEST", "{}");
        sent.markSent();
        when(repository.findById(sent.getId())).thenReturn(java.util.Optional.of(sent));
        NotificationService service = service(new RetryPolicy(3, List.of(Duration.ofSeconds(2))));

        service.attemptNow(sent.getId());

        verify(sender, never()).send(any());
    }

    @Test
    void attemptNowFailsForAnUnknownNotification() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(java.util.Optional.empty());
        NotificationService service = service(new RetryPolicy(3, List.of(Duration.ofSeconds(2))));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.attemptNow(id))
                .isInstanceOf(com.enruta.common.error.BusinessException.class);
    }

    @Test
    void byShipmentAndFindReadFromTheRepository() {
        UUID shipmentId = UUID.randomUUID();
        Notification notification =
                new Notification(shipmentId, "EMAIL", "cliente@enruta.test", "TEST", "{}");
        when(repository.findByShipmentIdOrderByCreatedAtDesc(shipmentId))
                .thenReturn(List.of(notification));
        when(repository.findById(notification.getId()))
                .thenReturn(java.util.Optional.of(notification));
        NotificationService service = service(new RetryPolicy(3, List.of(Duration.ofSeconds(2))));

        assertThat(service.byShipment(shipmentId)).containsExactly(notification);
        assertThat(service.find(notification.getId())).isEqualTo(notification);
    }

    @Test
    void findFailsForAnUnknownNotification() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(java.util.Optional.empty());
        NotificationService service = service(new RetryPolicy(3, List.of(Duration.ofSeconds(2))));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.find(id))
                .isInstanceOf(com.enruta.common.error.BusinessException.class);
    }

    @Test
    void dlqFailureDoesNotEscape() throws Exception {
        stubSave();
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp caido"))
                .when(sender)
                .send(any());
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("kafka caido"));
        NotificationService service = service(new RetryPolicy(1, List.of(Duration.ofSeconds(2))));

        Notification notification =
                service.createAndAttempt(
                        UUID.randomUUID(), "EMAIL", "cliente@enruta.test", "TEST", "{}");

        assertThat(notification.getStatus()).isEqualTo(Notification.Status.FAILED);
    }
}
