package com.enruta.notification.repository;

import com.enruta.notification.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByShipmentIdOrderByCreatedAtDesc(UUID shipmentId);

    /**
     * Reserva las notificaciones vencidas.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} permite varias replicas sin enviar dos veces el mismo
     * correo.
     */
    @Query(
            value =
                    """
          SELECT * FROM notifications
           WHERE status = 'PENDING' AND next_attempt_at <= now()
           ORDER BY next_attempt_at
           LIMIT :limit
           FOR UPDATE SKIP LOCKED
          """,
            nativeQuery = true)
    List<Notification> lockDue(int limit);
}
