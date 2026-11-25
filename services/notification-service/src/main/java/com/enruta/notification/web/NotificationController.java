package com.enruta.notification.web;

import com.enruta.common.error.BusinessException;
import com.enruta.notification.domain.Notification;
import com.enruta.notification.repository.NotificationRepository;
import com.enruta.notification.service.NotificationService;
import com.enruta.notification.service.TemplateRenderer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
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
    private final NotificationRepository repository;
    private final TemplateRenderer templates;

    public NotificationController(
            NotificationService notifications,
            NotificationRepository repository,
            TemplateRenderer templates) {
        this.notifications = notifications;
        this.repository = repository;
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
        requireAdmin(jwt);
        Notification notification =
                notifications.createAndAttempt(
                        null,
                        "EMAIL",
                        request.recipient(),
                        "TEST",
                        templates.toJson(Map.of("shipmentId", "test")));
        return NotificationView.from(notification);
    }

    /**
     * Fuerza un intento de envio sobre una notificacion pendiente.
     *
     * <p>Sirve cuando SMTP ya se recupero pero el reintento automatico aun no ha llegado.
     */
    @PostMapping("/{id}/retry")
    public NotificationView retry(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return NotificationView.from(notifications.attemptNow(id));
    }

    /** Cancela una notificacion pendiente antes de que el scheduler la reintente. */
    @PostMapping("/{id}/cancel")
    public NotificationView cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return NotificationView.from(notifications.cancel(id));
    }

    /** Ultimas 50 notificaciones fallidas, para triage. */
    @GetMapping("/failed")
    public List<NotificationView> failed(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return notifications.failed().stream().map(NotificationView::from).toList();
    }

    /** Conteo por estado: lo consume el tablero de Grafana y el panel de administracion. */
    @GetMapping("/stats")
    public Map<String, Long> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (Notification.Status status : Notification.Status.values()) {
            stats.put(status.name(), repository.countByStatus(status));
        }
        stats.put("TOTAL", repository.count());
        return stats;
    }

    private void requireAdmin(Jwt jwt) {
        List<String> roles = jwt == null ? null : jwt.getClaimAsStringList("roles");
        if (roles == null || !roles.contains("ADMIN")) {
            throw new BusinessException("FORBIDDEN", "Solo un administrador", HttpStatus.FORBIDDEN);
        }
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
