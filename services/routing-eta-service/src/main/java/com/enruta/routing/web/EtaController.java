package com.enruta.routing.web;

import com.enruta.common.error.BusinessException;
import com.enruta.routing.service.EtaService;
import com.enruta.routing.service.RouteResult;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/eta")
public class EtaController {

    private final EtaService etaService;

    public EtaController(EtaService etaService) {
        this.etaService = etaService;
    }

    @GetMapping
    public RouteResult eta(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double fromLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double fromLng,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double toLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double toLng) {

        if (Math.abs(fromLat - toLat) < 1e-7 && Math.abs(fromLng - toLng) < 1e-7) {
            throw BusinessException.badRequest("SAME_POINT", "Origen y destino son el mismo punto");
        }
        return etaService.route(fromLat, fromLng, toLat, toLng);
    }
}
