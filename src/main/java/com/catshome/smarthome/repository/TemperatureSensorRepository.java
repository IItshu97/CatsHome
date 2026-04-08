package com.catshome.smarthome.repository;

import com.catshome.smarthome.entity.TemperatureSensor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemperatureSensorRepository extends JpaRepository<TemperatureSensor, Long> {
    List<TemperatureSensor> findByRoomId(Long roomId);
    Optional<TemperatureSensor> findByTopic(String topic);
    boolean existsByName(String name);
    boolean existsByAddress(String address);
}