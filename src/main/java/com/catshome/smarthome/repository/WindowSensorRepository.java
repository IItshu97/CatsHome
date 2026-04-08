package com.catshome.smarthome.repository;

import com.catshome.smarthome.entity.WindowSensor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WindowSensorRepository extends JpaRepository<WindowSensor, Long> {
    List<WindowSensor> findByRoomId(Long roomId);
    Optional<WindowSensor> findByTopic(String topic);
    boolean existsByName(String name);
    boolean existsByAddress(String address);
}