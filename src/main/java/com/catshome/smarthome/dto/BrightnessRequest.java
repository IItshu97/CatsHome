package com.catshome.smarthome.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BrightnessRequest(
        @NotNull(message = "Value is required")
        @Min(value = 0, message = "Brightness must be 0–100")
        @Max(value = 100, message = "Brightness must be 0–100")
        Integer value
) {}
