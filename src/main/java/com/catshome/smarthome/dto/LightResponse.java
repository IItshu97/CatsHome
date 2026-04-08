package com.catshome.smarthome.dto;

import com.catshome.smarthome.entity.Light;
import java.time.Instant;

public record LightResponse(
        Long id, String name, String address, String topic,
        String state, Long roomId, Instant createdAt, Instant updatedAt
) {
    public static LightResponse from(Light l) {
        return new LightResponse(
                l.getId(), l.getName(), l.getAddress(), l.getTopic(),
                l.getState(), l.getRoom().getId(), l.getCreatedAt(), l.getUpdatedAt());
    }
}