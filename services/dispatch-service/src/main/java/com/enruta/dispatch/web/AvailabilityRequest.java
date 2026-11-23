package com.enruta.dispatch.web;

import com.enruta.dispatch.domain.DriverStatus;
import jakarta.validation.constraints.NotNull;

public record AvailabilityRequest(@NotNull DriverStatus status) {}
