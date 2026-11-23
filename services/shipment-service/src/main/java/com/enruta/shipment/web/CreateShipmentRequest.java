package com.enruta.shipment.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateShipmentRequest(
        @NotNull @Valid Coordinate pickup,
        @NotNull @Valid Coordinate dropoff,
        @Size(max = 300) String note) {

    public record Coordinate(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
            @NotBlank @Size(max = 300) String address) {}
}
