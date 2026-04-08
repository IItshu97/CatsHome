package com.catshome.smarthome.repository;

import com.catshome.smarthome.entity.Light;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LightRepository extends JpaRepository<Light, Long> {
    List<Light> findByRoomId(Long roomId);
    Optional<Light> findByTopic(String topic);
    boolean existsByName(String name);
    boolean existsByAddress(String address);
}