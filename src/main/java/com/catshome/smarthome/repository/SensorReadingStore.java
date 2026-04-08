package com.catshome.smarthome.repository;

import com.catshome.smarthome.dto.SensorReadingPoint;

import java.time.Instant;
import java.util.List;

public interface SensorReadingStore {
    void save(Long deviceId, String deviceType, Long roomId, Instant timestamp, String payload);
    List<SensorReadingPoint> findByDeviceIdBetween(Long deviceId, Instant from, Instant to);
}