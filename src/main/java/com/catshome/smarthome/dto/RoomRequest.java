package com.catshome.smarthome.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoomRequest(
        @NotBlank(message = "Room name is required")
        @Size(max = 255, message = "Room name must be at most 255 characters")
        String name
) {}