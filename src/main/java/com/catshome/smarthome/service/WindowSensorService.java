package com.catshome.smarthome.service;

import com.catshome.smarthome.dto.DeviceRequest;
import com.catshome.smarthome.dto.WindowSensorResponse;
import com.catshome.smarthome.entity.Room;
import com.catshome.smarthome.entity.WindowSensor;
import com.catshome.smarthome.exception.DuplicateResourceException;
import com.catshome.smarthome.exception.ResourceNotFoundException;
import com.catshome.smarthome.repository.WindowSensorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.catshome.smarthome.service.LightService.buildTopic;

@Service
public class WindowSensorService {

    private final WindowSensorRepository repo;
    private final RoomService roomService;

    public WindowSensorService(WindowSensorRepository repo, RoomService roomService) {
        this.repo = repo;
        this.roomService = roomService;
    }

    public List<WindowSensorResponse> findAll() {
        return repo.findAll().stream().map(WindowSensorResponse::from).toList();
    }

    public WindowSensorResponse findById(Long id) {
        return WindowSensorResponse.from(getOrThrow(id));
    }

    public List<WindowSensorResponse> findByRoom(Long roomId) {
        return repo.findByRoomId(roomId).stream().map(WindowSensorResponse::from).toList();
    }

    @Transactional
    public WindowSensorResponse create(DeviceRequest req) {
        checkUniqueness(req, null);
        Room room = roomService.getOrThrow(req.roomId());

        WindowSensor sensor = new WindowSensor();
        sensor.setName(req.name());
        sensor.setAddress(req.address());
        sensor.setRoom(room);
        sensor.setTopic(buildTopic("window", room.getName(), req.name()));
        return WindowSensorResponse.from(repo.save(sensor));
    }

    @Transactional
    public WindowSensorResponse update(Long id, DeviceRequest req) {
        WindowSensor sensor = getOrThrow(id);
        checkUniqueness(req, id);
        Room room = roomService.getOrThrow(req.roomId());

        sensor.setName(req.name());
        sensor.setAddress(req.address());
        sensor.setRoom(room);
        sensor.setTopic(buildTopic("window", room.getName(), req.name()));
        return WindowSensorResponse.from(repo.save(sensor));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new ResourceNotFoundException("WindowSensor", id);
        }
        repo.deleteById(id);
    }

    private WindowSensor getOrThrow(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("WindowSensor", id));
    }

    private void checkUniqueness(DeviceRequest req, Long excludeId) {
        repo.findAll().stream()
                .filter(w -> excludeId == null || !w.getId().equals(excludeId))
                .forEach(w -> {
                    if (w.getName().equals(req.name()))
                        throw new DuplicateResourceException("Window sensor with name '" + req.name() + "' already exists");
                    if (w.getAddress().equals(req.address()))
                        throw new DuplicateResourceException("Window sensor with address '" + req.address() + "' already exists");
                });
    }
}