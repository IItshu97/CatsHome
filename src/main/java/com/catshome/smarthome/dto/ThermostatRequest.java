package com.catshome.smarthome.dto;

import jakarta.validation.constraints.*;

public record ThermostatRequest(
        @DecimalMin(value = "10.0", message = "Target temperature must be 10–30 °C")
        @DecimalMax(value = "30.0", message = "Target temperature must be 10–30 °C")
        Double target,

        @Pattern(regexp = "heat|off", message = "Mode must be 'heat' or 'off'")
        String mode
) {}