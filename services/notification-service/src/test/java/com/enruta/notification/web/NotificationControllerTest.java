package com.enruta.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.common.error.BusinessException;
import com.enruta.notification.domain.Notification;
import com.enruta.notification.repository.NotificationRepository;
import com.enruta.notification.service.NotificationService;
import com.enruta.notification.service.TemplateRenderer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class NotificationControllerTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final TemplateRenderer templates = mock(TemplateRenderer.class);

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notifications, repository, templates);
    }

    private Jwt jwtWith(String... roles) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsStringList("roles")).thenReturn(List.of(roles));
        return jwt;
    }

    @Test
    void listsNotificationsOfAShipment() {
        UUID shipmentId = UUID.randomUUID();
        Notification notification =
                new Notification(shipmentId, "EMAIL", "cliente@enruta.test", "TEST", "{}");
        when(notifications.byShipment(shipmentId)).thenReturn(List.of(notification));

        List<NotificationController.NotificationView> views = controller.byShipment(shipmentId);

        assertThat(views).hasSize(1);
        assertThat(views.getFirst().recipient()).isEqualTo("cliente@enruta.test");
    }

    @Test
    void getsOneNotification() {
        UUID id = UUID.randomUUID();
        Notification notification =
                new Notification(null, "EMAIL", "cliente@enruta.test", "TEST", "{}");
        when(notifications.find(id)).thenReturn(notification);

        assertThat(controller.get(id).id()).isEqualTo(notification.getId());
    }

    @Test
    void adminCanSendATestNotification() {
        when(templates.toJson(any())).thenReturn("{}");
        when(notifications.createAndAttempt(
                        isNull(), eq("EMAIL"), eq("prueba@enruta.test"), eq("TEST"), anyString()))
                .thenReturn(new Notification(null, "EMAIL", "prueba@enruta.test", "TEST", "{}"));

        var response =
                controller.test(
                        new NotificationController.TestRequest("prueba@enruta.test"),
                        jwtWith("ADMIN"));

        assertThat(response.recipient()).isEqualTo("prueba@enruta.test");
        verify(notifications)
                .createAndAttempt(
                        isNull(), eq("EMAIL"), eq("prueba@enruta.test"), eq("TEST"), eq("{}"));
    }

    @Test
    void nonAdminsCannotSendTestNotifications() {
        assertThatThrownBy(
                        () ->
                                controller.test(
                                        new NotificationController.TestRequest("x@enruta.test"),
                                        jwtWith("CLIENT")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void adminCanRetryANotification() {
        UUID id = UUID.randomUUID();
        Notification notification =
                new Notification(null, "EMAIL", "cliente@enruta.test", "TEST", "{}");
        when(notifications.attemptNow(id)).thenReturn(notification);

        assertThat(controller.retry(id, jwtWith("ADMIN")).id()).isEqualTo(notification.getId());
    }

    @Test
    void nonAdminsCannotRetry() {
        assertThatThrownBy(() -> controller.retry(UUID.randomUUID(), jwtWith("CLIENT")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void adminCanCancelAPendingNotification() {
        UUID id = UUID.randomUUID();
        Notification notification =
                new Notification(null, "EMAIL", "cliente@enruta.test", "TEST", "{}");
        notification.markFailed("cancelada manualmente");
        when(notifications.cancel(id)).thenReturn(notification);

        assertThat(controller.cancel(id, jwtWith("ADMIN")).status()).isEqualTo("FAILED");
    }

    @Test
    void nonAdminsCannotCancel() {
        assertThatThrownBy(() -> controller.cancel(UUID.randomUUID(), jwtWith("CLIENT")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void failedListsOnlyAdmins() {
        Notification failed = new Notification(null, "EMAIL", "cliente@enruta.test", "TEST", "{}");
        failed.markFailed("smtp caido");
        when(notifications.failed()).thenReturn(List.of(failed));

        assertThat(controller.failed(jwtWith("ADMIN"))).hasSize(1);
        assertThatThrownBy(() -> controller.failed(jwtWith("CLIENT")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void statsCountEveryStatus() {
        when(repository.countByStatus(any())).thenReturn(2L);
        when(repository.count()).thenReturn(6L);

        Map<String, Long> stats = controller.stats();

        assertThat(stats)
                .containsEntry("PENDING", 2L)
                .containsEntry("SENT", 2L)
                .containsEntry("FAILED", 2L)
                .containsEntry("TOTAL", 6L);
    }
}
