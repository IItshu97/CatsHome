package com.catshome.smarthome.dto;

import com.catshome.smarthome.entity.Device;
import com.catshome.smarthome.entity.DeviceType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

public record DeviceResponse(
        Long id,
        String name,
        DeviceType deviceType,
        boolean isDimmer,
        Long roomId,
        String ipAddress,
        String mqttTopic,
        boolean online,
        Instant lastSeen,
        Map<String, Object> state,
        String firmwareVersion,
        Instant createdAt,
        Instant updatedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static DeviceResponse from(Device d) {
        Map<String, Object> state = null;
        if (d.getStateJson() != null) {
            try {
                state = MAPPER.readValue(d.getStateJson(), new TypeReference<>() {});
            } catch (Exception ignored) {}
        }
        return new DeviceResponse(
                d.getId(), d.getName(), d.getDeviceType(), d.isDimmer(),
                d.getRoom().getId(), d.getIpAddress(), d.getMqttTopic(),
                d.isOnline(), d.getLastSeen(), state,
                d.getFirmwareVersion(), d.getCreatedAt(), d.getUpdatedAt());
    }
}