package com.catshome.smarthome.dto;

import java.time.Instant;

public record SensorReadingPoint(Long deviceId, Instant timestamp, String payload) {}