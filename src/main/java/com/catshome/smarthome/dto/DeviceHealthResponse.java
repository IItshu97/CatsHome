package com.catshome.smarthome.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Response from the ESPHome built-in web_server health endpoint:
 * {@code GET http://{device_ip}/health}
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeviceHealthResponse(
        String type,
        String firmware,
        Long uptimeMs,
        Integer wifiRssi,
        String wifiSsid,
        String ip,
        Boolean mqttConnected,
        String mqttTopic
) {}