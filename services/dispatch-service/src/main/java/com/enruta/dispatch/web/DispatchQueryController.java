package com.enruta.dispatch.web;

import com.enruta.dispatch.domain.DispatchState;
import com.enruta.dispatch.repository.DispatchStateRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Vista de diagnostico del despacho. Util para la demo y para depurar. */
@RestController
@RequestMapping("/api/v1/dispatch")
public class DispatchQueryController {

    private final DispatchStateRepository states;

    public DispatchQueryController(DispatchStateRepository states) {
        this.states = states;
    }

    public record DispatchStateView(
            UUID shipmentId, String phase, short round, UUID currentOfferId, Instant updatedAt) {}

    @GetMapping("/shipments/{shipmentId}")
    public DispatchStateView state(@PathVariable UUID shipmentId) {
        return states.findById(shipmentId)
                .map(DispatchQueryController::toView)
                .orElseThrow(
                        () ->
                                com.enruta.common.error.BusinessException.notFound(
                                        "DISPATCH_NOT_FOUND", "Envio sin despacho"));
    }

    private static DispatchStateView toView(DispatchState state) {
        return new DispatchStateView(
                state.getShipmentId(),
                state.getPhase().name(),
                state.getRound(),
                state.getCurrentOfferId(),
                state.getUpdatedAt());
    }
}
