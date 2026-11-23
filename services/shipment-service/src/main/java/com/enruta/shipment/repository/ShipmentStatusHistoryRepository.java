package com.enruta.shipment.repository;

import com.enruta.shipment.domain.ShipmentStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentStatusHistoryRepository
        extends JpaRepository<ShipmentStatusHistory, Long> {

    List<ShipmentStatusHistory> findByShipmentIdOrderByOccurredAtAsc(UUID shipmentId);
}
