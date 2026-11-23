package com.enruta.dispatch.repository;

import com.enruta.dispatch.domain.DispatchState;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchStateRepository extends JpaRepository<DispatchState, UUID> {}
