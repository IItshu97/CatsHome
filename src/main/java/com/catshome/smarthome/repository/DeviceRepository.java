package com.catshome.smarthome.repository;

import com.catshome.smarthome.entity.Device;
import com.catshome.smarthome.entity.DeviceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    @Query("""
            SELECT d FROM Device d
            WHERE (:type IS NULL OR d.deviceType = :type)
              AND (:roomId IS NULL OR d.room.id = :roomId)
              AND (:online IS NULL OR d.online = :online)
            """)
    List<Device> findWithFilters(
            @Param("type") DeviceType type,
            @Param("roomId") Long roomId,
            @Param("online") Boolean online);

    Optional<Device> findByMqttTopic(String mqttTopic);

    boolean existsByIpAddress(String ipAddress);

    boolean existsByDeviceTypeAndRoomIdAndName(DeviceType deviceType, Long roomId, String name);
}