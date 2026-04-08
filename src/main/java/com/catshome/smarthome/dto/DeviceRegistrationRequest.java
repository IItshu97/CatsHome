package com.catshome.smarthome.dto;

import com.catshome.smarthome.entity.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeviceRegistrationRequest(
        @NotBlank(message = "Device name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotNull(message = "Device type is required")
        DeviceType deviceType,

        Boolean isDimmer,

        @NotNull(message = "Room ID is required")
        Long roomId,

        @NotBlank(message = "IP address is required")
        @Pattern(regexp = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$",
                 message = "Must be a valid IPv4 address")
        String ipAddress
) {}