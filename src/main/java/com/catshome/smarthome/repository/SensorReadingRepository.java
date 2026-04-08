package com.catshome.smarthome.repository;

import com.catshome.smarthome.entity.SensorReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {
    List<SensorReading> findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(
            Long deviceId, Instant from, Instant to);
}