package com.catshome.smarthome.service;

import com.catshome.smarthome.dto.DeviceRequest;
import com.catshome.smarthome.dto.DoorSensorResponse;
import com.catshome.smarthome.entity.DoorSensor;
import com.catshome.smarthome.entity.Room;
import com.catshome.smarthome.exception.DuplicateResourceException;
import com.catshome.smarthome.exception.ResourceNotFoundException;
import com.catshome.smarthome.repository.DoorSensorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.catshome.smarthome.service.LightService.buildTopic;

@Service
public class DoorSensorService {

    private final DoorSensorRepository repo;
    private final RoomService roomService;

    public DoorSensorService(DoorSensorRepository repo, RoomService roomService) {
        this.repo = repo;
        this.roomService = roomService;
    }

    public List<DoorSensorResponse> findAll() {
        return repo.findAll().stream().map(DoorSensorResponse::from).toList();
    }

    public DoorSensorResponse findById(Long id) {
        return DoorSensorResponse.from(getOrThrow(id));
    }

    public List<DoorSensorResponse> findByRoom(Long roomId) {
        return repo.findByRoomId(roomId).stream().map(DoorSensorResponse::from).toList();
    }

    @Transactional
    public DoorSensorResponse create(DeviceRequest req) {
        checkUniqueness(req, null);
        Room room = roomService.getOrThrow(req.roomId());

        DoorSensor sensor = new DoorSensor();
        sensor.setName(req.name());
        sensor.setAddress(req.address());
        sensor.setRoom(room);
        sensor.setTopic(buildTopic("door", room.getName(), req.name()));
        return DoorSensorResponse.from(repo.save(sensor));
    }

    @Transactional
    public DoorSensorResponse update(Long id, DeviceRequest req) {
        DoorSensor sensor = getOrThrow(id);
        checkUniqueness(req, id);
        Room room = roomService.getOrThrow(req.roomId());

        sensor.setName(req.name());
        sensor.setAddress(req.address());
        sensor.setRoom(room);
        sensor.setTopic(buildTopic("door", room.getName(), req.name()));
        return DoorSensorResponse.from(repo.save(sensor));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new ResourceNotFoundException("DoorSensor", id);
        }
        repo.deleteById(id);
    }

    private DoorSensor getOrThrow(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("DoorSensor", id));
    }

    private void checkUniqueness(DeviceRequest req, Long excludeId) {
        repo.findAll().stream()
                .filter(d -> excludeId == null || !d.getId().equals(excludeId))
                .forEach(d -> {
                    if (d.getName().equals(req.name()))
                        throw new DuplicateResourceException("Door sensor with name '" + req.name() + "' already exists");
                    if (d.getAddress().equals(req.address()))
                        throw new DuplicateResourceException("Door sensor with address '" + req.address() + "' already exists");
                });
    }
}