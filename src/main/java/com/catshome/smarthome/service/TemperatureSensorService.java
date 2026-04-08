package com.catshome.smarthome.service;

import com.catshome.smarthome.dto.DeviceRequest;
import com.catshome.smarthome.dto.TemperatureSensorResponse;
import com.catshome.smarthome.entity.Room;
import com.catshome.smarthome.entity.TemperatureSensor;
import com.catshome.smarthome.exception.DuplicateResourceException;
import com.catshome.smarthome.exception.ResourceNotFoundException;
import com.catshome.smarthome.repository.TemperatureSensorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.catshome.smarthome.service.LightService.buildTopic;

@Service
public class TemperatureSensorService {

    private final TemperatureSensorRepository repo;
    private final RoomService roomService;

    public TemperatureSensorService(TemperatureSensorRepository repo, RoomService roomService) {
        this.repo = repo;
        this.roomService = roomService;
    }

    public List<TemperatureSensorResponse> findAll() {
        return repo.findAll().stream().map(TemperatureSensorResponse::from).toList();
    }

    public TemperatureSensorResponse findById(Long id) {
        return TemperatureSensorResponse.from(getOrThrow(id));
    }

    public List<TemperatureSensorResponse> findByRoom(Long roomId) {
        return repo.findByRoomId(roomId).stream().map(TemperatureSensorResponse::from).toList();
    }

    @Transactional
    public TemperatureSensorResponse create(DeviceRequest req) {
        checkUniqueness(req, null);
        Room room = roomService.getOrThrow(req.roomId());

        TemperatureSensor sensor = new TemperatureSensor();
        sensor.setName(req.name());
        sensor.setAddress(req.address());
        sensor.setRoom(room);
        sensor.setTopic(buildTopic("temperature", room.getName(), req.name()));
        return TemperatureSensorResponse.from(repo.save(sensor));
    }

    @Transactional
    public TemperatureSensorResponse update(Long id, DeviceRequest req) {
        TemperatureSensor sensor = getOrThrow(id);
        checkUniqueness(req, id);
        Room room = roomService.getOrThrow(req.roomId());

        sensor.setName(req.name());
        sensor.setAddress(req.address());
        sensor.setRoom(room);
        sensor.setTopic(buildTopic("temperature", room.getName(), req.name()));
        return TemperatureSensorResponse.from(repo.save(sensor));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new ResourceNotFoundException("TemperatureSensor", id);
        }
        repo.deleteById(id);
    }

    private TemperatureSensor getOrThrow(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("TemperatureSensor", id));
    }

    private void checkUniqueness(DeviceRequest req, Long excludeId) {
        repo.findAll().stream()
                .filter(t -> excludeId == null || !t.getId().equals(excludeId))
                .forEach(t -> {
                    if (t.getName().equals(req.name()))
                        throw new DuplicateResourceException("Temperature sensor with name '" + req.name() + "' already exists");
                    if (t.getAddress().equals(req.address()))
                        throw new DuplicateResourceException("Temperature sensor with address '" + req.address() + "' already exists");
                });
    }
}