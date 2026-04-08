package com.catshome.smarthome.dto;

import com.catshome.smarthome.entity.WindowSensor;
import java.time.Instant;

public record WindowSensorResponse(
        Long id, String name, String address, String topic,
        String state, Long roomId, Instant createdAt, Instant updatedAt
) {
    public static WindowSensorResponse from(WindowSensor w) {
        return new WindowSensorResponse(
                w.getId(), w.getName(), w.getAddress(), w.getTopic(),
                w.getState(), w.getRoom().getId(), w.getCreatedAt(), w.getUpdatedAt());
    }
}