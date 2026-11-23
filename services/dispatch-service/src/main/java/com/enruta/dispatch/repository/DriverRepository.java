package com.enruta.dispatch.repository;

import com.enruta.dispatch.domain.Driver;
import com.enruta.dispatch.domain.DriverStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    List<Driver> findByStatus(DriverStatus status);
}
