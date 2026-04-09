package com.catshome.smarthome.service;

import com.catshome.smarthome.dto.DeviceHealthResponse;

import java.util.Optional;

/**
 * Fetches health data from a single ESPHome device.
 * Separated as an interface so the poller can be unit-tested without a real HTTP server.
 */
public interface DeviceHealthClient {
    Optional<DeviceHealthResponse> fetchHealth(String ipAddress);
}
