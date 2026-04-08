package com.catshome.smarthome.dto;

import com.catshome.smarthome.entity.Room;
import java.time.Instant;

public record RoomResponse(Long id, String name, Instant createdAt, Instant updatedAt) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getCreatedAt(), room.getUpdatedAt());
    }
}