package com.catshome.smarthome.repository;

import com.catshome.smarthome.entity.DeviceStateLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface DeviceStateLogRepository extends JpaRepository<DeviceStateLog, Long> {
    List<DeviceStateLog> findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(
            Long deviceId, Instant from, Instant to);
}