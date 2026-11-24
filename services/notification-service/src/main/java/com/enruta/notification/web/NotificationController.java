package com.enruta.notification.web;

import com.enruta.common.error.BusinessException;
import com.enruta.notification.domain.Notification;
import com.enruta.notification.service.NotificationService;
import com.enruta.notification.service.TemplateRenderer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final TemplateRenderer templates;

    public NotificationController(NotificationService notifications, TemplateRenderer templates) {
        this.notifications = notifications;
        this.templates = templates;
    }

    public record TestRequest(@NotBlank @Email String recipient) {}

    @GetMapping
    public List<NotificationView> byShipment(@RequestParam UUID shipmentId) {
        return notifications.byShipment(shipmentId).stream().map(NotificationView::from).toList();
    }

    @GetMapping("/{id}")
    public NotificationView get(@PathVariable UUID id) {
        return NotificationView.from(notifications.find(id));
    }

    /**
     * Encola una notificacion de prueba. Util para demostrar los reintentos y la DLQ sin esperar a
     * que un envio se entregue.
     */
    @PostMapping("/test")
    public NotificationView test(
            @Valid @RequestBody TestRequest request, @AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || !roles.contains("ADMIN")) {
            throw new BusinessException("FORBIDDEN", "Solo un administrador", HttpStatus.FORBIDDEN);
        }
        Notification notification =
                notifications.createAndAttempt(
                        null,
                        "EMAIL",
                        request.recipient(),
                        "TEST",
                        templates.toJson(Map.of("shipmentId", "test")));
        return NotificationView.from(notification);
    }

    public record NotificationView(
            UUID id,
            UUID shipmentId,
            String channel,
            String recipient,
            String template,
            String status,
            short attempts,
            String lastError,
            java.time.Instant createdAt,
            java.time.Instant sentAt) {

        public static NotificationView from(Notification n) {
            return new NotificationView(
                    n.getId(),
                    n.getShipmentId(),
                    n.getChannel(),
                    n.getRecipient(),
                    n.getTemplate(),
                    n.getStatus().name(),
                    n.getAttempts(),
                    n.getLastError(),
                    n.getCreatedAt(),
                    n.getSentAt());
        }
    }
}
