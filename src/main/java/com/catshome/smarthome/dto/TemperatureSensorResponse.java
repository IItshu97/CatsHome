package com.catshome.smarthome.dto;

import com.catshome.smarthome.entity.TemperatureSensor;
import java.time.Instant;

public record TemperatureSensorResponse(
        Long id, String name, String address, String topic,
        Double temperature, Double humidity,
        Long roomId, Instant createdAt, Instant updatedAt
) {
    public static TemperatureSensorResponse from(TemperatureSensor t) {
        return new TemperatureSensorResponse(
                t.getId(), t.getName(), t.getAddress(), t.getTopic(),
                t.getTemperature(), t.getHumidity(),
                t.getRoom().getId(), t.getCreatedAt(), t.getUpdatedAt());
    }
}