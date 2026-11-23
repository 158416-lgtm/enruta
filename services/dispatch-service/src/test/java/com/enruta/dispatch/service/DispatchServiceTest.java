package com.enruta.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enruta.common.outbox.OutboxAppender;
import com.enruta.dispatch.domain.Assignment;
import com.enruta.dispatch.domain.DispatchState;
import com.enruta.dispatch.domain.Driver;
import com.enruta.dispatch.domain.DriverStatus;
import com.enruta.dispatch.repository.AssignmentRepository;
import com.enruta.dispatch.repository.DispatchStateRepository;
import com.enruta.dispatch.repository.DriverRepository;
import com.enruta.dispatch.repository.OfferRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock private DriverRepository drivers;
    @Mock private OfferRepository offers;
    @Mock private AssignmentRepository assignments;
    @Mock private DispatchStateRepository states;
    @Mock private CandidateFinder finder;
    @Mock private OutboxAppender outbox;
    @Mock private StringRedisTemplate redis;

    private DispatchService service;

    @BeforeEach
    void setUp() {
        service = new DispatchService(drivers, offers, assignments, states, finder, outbox, redis);
    }

    @Test
    void finishingADeliveryFreesTheDriver() {
        UUID shipmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Assignment assignment = new Assignment(shipmentId, driverId);
        Driver driver = new Driver(driverId, "Ana", "MOTO", 4.9, 4.3449, -74.3674);
        driver.markBusy();
        DispatchState state = new DispatchState(shipmentId, 4.3449, -74.3674);

        when(assignments.findFirstByShipmentIdAndStatus(shipmentId, Assignment.Status.ACTIVE))
                .thenReturn(Optional.of(assignment));
        when(drivers.findById(driverId)).thenReturn(Optional.of(driver));
        when(states.findById(shipmentId)).thenReturn(Optional.of(state));

        service.finishDelivery(shipmentId);

        assertThat(assignment.getStatus()).isEqualTo(Assignment.Status.COMPLETED);
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(state.getPhase()).isEqualTo(DispatchState.Phase.COMPLETED);
        verify(finder).index(driverId, 4.3449, -74.3674);
    }

    @Test
    void finishingWithoutActiveAssignmentIsHarmless() {
        UUID shipmentId = UUID.randomUUID();
        when(assignments.findFirstByShipmentIdAndStatus(shipmentId, Assignment.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(states.findById(shipmentId)).thenReturn(Optional.empty());

        service.finishDelivery(shipmentId);

        verify(drivers, org.mockito.Mockito.never()).findById(any());
    }
}
