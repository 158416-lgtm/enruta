package com.enruta.dispatch.repository;

import com.enruta.dispatch.domain.Assignment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findFirstByShipmentIdAndStatus(UUID shipmentId, Assignment.Status status);

    boolean existsByShipmentIdAndStatus(UUID shipmentId, Assignment.Status status);
}
