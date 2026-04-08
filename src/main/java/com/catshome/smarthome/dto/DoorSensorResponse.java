package com.catshome.smarthome.dto;

import com.catshome.smarthome.entity.DoorSensor;
import java.time.Instant;

public record DoorSensorResponse(
        Long id, String name, String address, String topic,
        String state, Long roomId, Instant createdAt, Instant updatedAt
) {
    public static DoorSensorResponse from(DoorSensor d) {
        return new DoorSensorResponse(
                d.getId(), d.getName(), d.getAddress(), d.getTopic(),
                d.getState(), d.getRoom().getId(), d.getCreatedAt(), d.getUpdatedAt());
    }
}