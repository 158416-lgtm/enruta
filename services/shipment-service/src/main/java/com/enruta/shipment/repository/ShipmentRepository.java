package com.enruta.shipment.repository;

import com.enruta.shipment.domain.Shipment;
import com.enruta.shipment.domain.ShipmentStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Page<Shipment> findByStatus(ShipmentStatus status, Pageable pageable);

    Page<Shipment> findByCustomerId(UUID customerId, Pageable pageable);
}
