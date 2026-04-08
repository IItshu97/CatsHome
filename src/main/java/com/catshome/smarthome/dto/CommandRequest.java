package com.catshome.smarthome.dto;

import jakarta.validation.constraints.NotBlank;

public record CommandRequest(
        @NotBlank(message = "Command is required")
        String command
) {}