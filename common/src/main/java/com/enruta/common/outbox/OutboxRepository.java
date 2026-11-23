package com.enruta.common.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Reserva un lote de eventos sin publicar.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} permite que varias instancias del relay convivan sin
     * publicar el mismo evento dos veces.
     */
    @Query(
            value =
                    """
          SELECT * FROM outbox
           WHERE published_at IS NULL
           ORDER BY created_at
           LIMIT :limit
           FOR UPDATE SKIP LOCKED
          """,
            nativeQuery = true)
    List<OutboxEvent> lockPending(@Param("limit") int limit);

    long countByPublishedAtIsNull();
}
