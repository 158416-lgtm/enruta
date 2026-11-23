package com.enruta.tracking.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Lote de posiciones. Se acepta mas de una para que el cliente movil pueda agrupar pings cuando
 * recupera cobertura, sin multiplicar peticiones.
 */
public record LocationBatchRequest(@NotEmpty @Size(max = 10) List<@Valid Point> points) {

    public record Point(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng) {}
}
