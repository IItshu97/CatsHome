package com.catshome.smarthome.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceRequest(
        @NotBlank(message = "Device name is required")
        @Size(max = 255, message = "Device name must be at most 255 characters")
        String name,

        @NotBlank(message = "Device address (IP) is required")
        @Size(max = 255, message = "Address must be at most 255 characters")
        String address,

        @NotNull(message = "Room ID is required")
        Long roomId
) {}