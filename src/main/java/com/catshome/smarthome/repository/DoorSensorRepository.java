package com.catshome.smarthome.repository;

import com.catshome.smarthome.entity.DoorSensor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoorSensorRepository extends JpaRepository<DoorSensor, Long> {
    List<DoorSensor> findByRoomId(Long roomId);
    Optional<DoorSensor> findByTopic(String topic);
    boolean existsByName(String name);
    boolean existsByAddress(String address);
}