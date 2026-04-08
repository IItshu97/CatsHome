package com.catshome.smarthome.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PositionRequest(
        @NotNull(message = "Position is required")
        @Min(value = 0, message = "Position must be 0–100")
        @Max(value = 100, message = "Position must be 0–100")
        Integer position
) {}