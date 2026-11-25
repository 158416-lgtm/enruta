package com.enruta.shipment.service;

import com.enruta.common.error.BusinessException;
import com.enruta.common.outbox.OutboxAppender;
import com.enruta.contracts.EventEnvelope;
import com.enruta.contracts.EventTypes;
import com.enruta.contracts.Topics;
import com.enruta.shipment.domain.Shipment;
import com.enruta.shipment.domain.ShipmentStatus;
import com.enruta.shipment.domain.ShipmentStatusHistory;
import com.enruta.shipment.repository.ShipmentRepository;
import com.enruta.shipment.repository.ShipmentStatusHistoryRepository;
import com.enruta.shipment.web.CreateShipmentRequest;
import com.enruta.shipment.web.ShipmentView;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShipmentRepository shipments;
    private final ShipmentStatusHistoryRepository history;
    private final OutboxAppender outbox;

    public ShipmentService(
            ShipmentRepository shipments,
            ShipmentStatusHistoryRepository history,
            OutboxAppender outbox) {
        this.shipments = shipments;
        this.history = history;
        this.outbox = outbox;
    }

    @Transactional
    public ShipmentView create(
            UUID customerId, String customerEmail, CreateShipmentRequest request) {
        Shipment shipment =
                new Shipment(
                        customerId,
                        customerEmail,
                        request.pickup().lat(),
                        request.pickup().lng(),
                        request.dropoff().lat(),
                        request.dropoff().lng(),
                        request.pickup().address(),
                        request.dropoff().address(),
                        newPickupPin());
        shipments.save(shipment);

        history.save(
                new ShipmentStatusHistory(
                        shipment.getId(),
                        null,
                        ShipmentStatus.CREATED,
                        customerId,
                        "envio creado"));

        outbox.append(
                EventEnvelope.of(
                        EventTypes.SHIPMENT_CREATED,
                        1,
                        shipment.getId(),
                        null,
                        ShipmentEventPayload.from(shipment)),
                Topics.SHIPMENT_EVENTS);

        log.info("envio creado id={} customer={}", shipment.getId(), customerId);
        return ShipmentView.from(shipment, List.of());
    }

    @Transactional(readOnly = true)
    public ShipmentView get(UUID shipmentId, UUID actorId, List<String> roles) {
        Shipment shipment = load(shipmentId);
        assertCanRead(shipment, actorId, roles);
        return ShipmentView.from(
                shipment, history.findByShipmentIdOrderByOccurredAtAsc(shipmentId));
    }

    /** Bitacora de estados del envio, con control de acceso. */
    @Transactional(readOnly = true)
    public List<ShipmentStatusHistory> history(UUID shipmentId, UUID actorId, List<String> roles) {
        Shipment shipment = load(shipmentId);
        assertCanRead(shipment, actorId, roles);
        return history.findByShipmentIdOrderByOccurredAtAsc(shipmentId);
    }

    @Transactional
    public ShipmentView cancel(UUID shipmentId, UUID actorId, List<String> roles) {
        Shipment shipment = load(shipmentId);
        if (!shipment.getStatus().canTransitionTo(ShipmentStatus.CANCELLED)) {
            throw BusinessException.conflict(
                    "INVALID_TRANSITION",
                    "El envio no se puede cancelar en estado " + shipment.getStatus());
        }
        boolean admin = roles.contains("ADMIN");
        boolean owner = shipment.getCustomerId().equals(actorId);
        if (!admin && !owner) {
            throw new BusinessException(
                    "FORBIDDEN",
                    "Solo el dueno o un administrador pueden cancelar",
                    HttpStatus.FORBIDDEN);
        }
        if (!admin && !shipment.getStatus().isCancellableByCustomer()) {
            throw BusinessException.conflict(
                    "CANCEL_NOT_ALLOWED", "El envio ya fue recogido y no se puede cancelar");
        }
        transition(shipment, ShipmentStatus.CANCELLED, actorId, null);
        return ShipmentView.from(shipment, List.of());
    }

    @Transactional
    public ShipmentView assign(UUID shipmentId, UUID driverId, UUID actorId) {
        Shipment shipment = load(shipmentId);
        if (shipment.getStatus() != ShipmentStatus.CREATED
                && shipment.getStatus() != ShipmentStatus.NO_DRIVER) {
            throw BusinessException.conflict(
                    "ASSIGN_NOT_ALLOWED", "Solo se asigna un envio pendiente de conductor");
        }
        shipment.assignTo(driverId);
        history.save(
                new ShipmentStatusHistory(
                        shipment.getId(),
                        ShipmentStatus.CREATED,
                        ShipmentStatus.ASSIGNED,
                        actorId,
                        null));
        outbox.append(
                EventEnvelope.of(
                        EventTypes.SHIPMENT_ASSIGNED,
                        1,
                        shipment.getId(),
                        null,
                        ShipmentEventPayload.from(shipment)),
                Topics.SHIPMENT_EVENTS);
        return ShipmentView.from(shipment, List.of());
    }

    /** Listado para la UI: el cliente ve lo suyo, el admin ve todo. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ShipmentView> list(
            UUID actorId,
            List<String> roles,
            ShipmentStatus status,
            org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<Shipment> page;
        if (roles.contains("ADMIN")) {
            page =
                    status == null
                            ? shipments.findAll(pageable)
                            : shipments.findByStatus(status, pageable);
        } else {
            page =
                    status == null
                            ? shipments.findByCustomerId(actorId, pageable)
                            : shipments.findByCustomerIdAndStatus(actorId, status, pageable);
        }
        return page.map(shipment -> ShipmentView.from(shipment, List.of()));
    }

    /** Reaccion a {@code dispatch.assigned}: el despacho eligio conductor. */
    @Transactional
    public void onDispatchAssigned(UUID shipmentId, UUID driverId) {
        Shipment shipment = load(shipmentId);
        if (shipment.getStatus() == ShipmentStatus.ASSIGNED
                && driverId.equals(shipment.getDriverId())) {
            return;
        }
        if (shipment.getStatus().isTerminal()) {
            log.warn(
                    "asignacion tardia ignorada shipment={} status={}",
                    shipmentId,
                    shipment.getStatus());
            return;
        }
        ShipmentStatus previous = shipment.getStatus();
        shipment.assignTo(driverId);
        history.save(
                new ShipmentStatusHistory(
                        shipmentId,
                        previous,
                        ShipmentStatus.ASSIGNED,
                        null,
                        "asignado por dispatch"));
        outbox.append(
                EventEnvelope.of(
                        EventTypes.SHIPMENT_ASSIGNED,
                        1,
                        shipmentId,
                        null,
                        ShipmentEventPayload.from(shipment)),
                Topics.SHIPMENT_EVENTS);
    }

    /** Compensacion de la saga: el conductor abandono antes de recoger. */
    @Transactional
    public void onDriverCancelled(UUID shipmentId) {
        Shipment shipment = load(shipmentId);
        if (shipment.getStatus() != ShipmentStatus.ASSIGNED) {
            return;
        }
        shipment.unassign();
        history.save(
                new ShipmentStatusHistory(
                        shipmentId,
                        ShipmentStatus.ASSIGNED,
                        ShipmentStatus.CREATED,
                        null,
                        "conductor cancelo la asignacion"));
        outbox.append(
                EventEnvelope.of(
                        EventTypes.SHIPMENT_UNASSIGNED,
                        1,
                        shipmentId,
                        null,
                        ShipmentEventPayload.from(shipment)),
                Topics.SHIPMENT_EVENTS);
    }

    /** Reaccion a {@code dispatch.failed}: se agotaron las rondas sin conductor. */
    @Transactional
    public void onNoDriver(UUID shipmentId) {
        Shipment shipment = load(shipmentId);
        if (shipment.getStatus() != ShipmentStatus.CREATED) {
            return;
        }
        shipment.transitionTo(ShipmentStatus.NO_DRIVER);
        history.save(
                new ShipmentStatusHistory(
                        shipmentId,
                        ShipmentStatus.CREATED,
                        ShipmentStatus.NO_DRIVER,
                        null,
                        "sin conductores disponibles"));
        outbox.append(
                EventEnvelope.of(
                        EventTypes.SHIPMENT_NO_DRIVER,
                        1,
                        shipmentId,
                        null,
                        ShipmentEventPayload.from(shipment)),
                Topics.SHIPMENT_EVENTS);
    }

    /** Reaccion a {@code tracking.geofence-entered} (PICKUP): el conductor llego al origen. */
    @Transactional
    public void onPickupConfirmed(UUID shipmentId) {
        Shipment shipment = load(shipmentId);
        if (shipment.getStatus() != ShipmentStatus.ASSIGNED) {
            return;
        }
        transition(
                shipment, ShipmentStatus.PICKED_UP, shipment.getDriverId(), "geocerca de origen");
    }

    /** El conductor ya se alejo del origen: el envio esta en camino. */
    @Transactional
    public void onTripStarted(UUID shipmentId) {
        Shipment shipment = load(shipmentId);
        if (shipment.getStatus() != ShipmentStatus.PICKED_UP) {
            return;
        }
        transition(shipment, ShipmentStatus.IN_TRANSIT, shipment.getDriverId(), "en camino");
    }

    /** Reaccion a {@code tracking.geofence-entered} (DROPOFF): llego al destino. */
    @Transactional
    public void onDelivered(UUID shipmentId) {
        Shipment shipment = load(shipmentId);
        if (shipment.getStatus() != ShipmentStatus.IN_TRANSIT) {
            log.warn("entrega ignorada shipment={} status={}", shipmentId, shipment.getStatus());
            return;
        }
        transition(
                shipment, ShipmentStatus.DELIVERED, shipment.getDriverId(), "geocerca de destino");
        outbox.append(
                EventEnvelope.of(
                        EventTypes.SHIPMENT_DELIVERED,
                        1,
                        shipmentId,
                        null,
                        ShipmentEventPayload.from(shipment)),
                Topics.SHIPMENT_EVENTS);

        if (shipment.getCustomerEmail() != null) {
            outbox.append(
                    EventEnvelope.of(
                            EventTypes.NOTIFICATION_REQUESTED,
                            1,
                            shipmentId,
                            null,
                            new NotificationCommand(
                                    shipmentId,
                                    shipment.getCustomerEmail(),
                                    "EMAIL",
                                    "SHIPMENT_DELIVERED",
                                    java.util.Map.of(
                                            "shipmentId", shipmentId.toString(),
                                            "dropoffAddress", shipment.getDropoffAddress()),
                                    "shipment:" + shipmentId + ":DELIVERED")),
                    Topics.NOTIFICATION_COMMANDS);
        }
    }

    /** Transicion generica usada por los consumidores de eventos en sprints posteriores. */
    @Transactional
    public void transition(Shipment shipment, ShipmentStatus next, UUID actorId, String reason) {
        ShipmentStatus previous = shipment.getStatus();
        try {
            shipment.transitionTo(next);
        } catch (IllegalStateException ex) {
            throw BusinessException.conflict(
                    "INVALID_TRANSITION", "Transicion no permitida desde " + previous);
        }
        history.save(new ShipmentStatusHistory(shipment.getId(), previous, next, actorId, reason));
        outbox.append(
                EventEnvelope.of(
                        EventTypes.SHIPMENT_STATUS_CHANGED,
                        1,
                        shipment.getId(),
                        null,
                        ShipmentEventPayload.from(shipment)),
                Topics.SHIPMENT_EVENTS);
    }

    private Shipment load(UUID shipmentId) {
        return shipments
                .findById(shipmentId)
                .orElseThrow(
                        () ->
                                BusinessException.notFound(
                                        "SHIPMENT_NOT_FOUND", "Envio inexistente"));
    }

    private void assertCanRead(Shipment shipment, UUID actorId, List<String> roles) {
        if (roles.contains("ADMIN")) {
            return;
        }
        if (roles.contains("DRIVER") && actorId.equals(shipment.getDriverId())) {
            return;
        }
        if (shipment.getCustomerId().equals(actorId)) {
            return;
        }
        throw new BusinessException(
                "FORBIDDEN", "No tienes acceso a este envio", HttpStatus.FORBIDDEN);
    }

    private String newPickupPin() {
        return String.format("%04d", RANDOM.nextInt(10_000));
    }
}
