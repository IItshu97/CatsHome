package com.catshome.smarthome.service;

import com.catshome.smarthome.dto.DeviceRequest;
import com.catshome.smarthome.dto.LightResponse;
import com.catshome.smarthome.entity.Light;
import com.catshome.smarthome.entity.Room;
import com.catshome.smarthome.exception.DuplicateResourceException;
import com.catshome.smarthome.exception.ResourceNotFoundException;
import com.catshome.smarthome.repository.LightRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LightService {

    private final LightRepository repo;
    private final RoomService roomService;

    public LightService(LightRepository repo, RoomService roomService) {
        this.repo = repo;
        this.roomService = roomService;
    }

    public List<LightResponse> findAll() {
        return repo.findAll().stream().map(LightResponse::from).toList();
    }

    public LightResponse findById(Long id) {
        return LightResponse.from(getOrThrow(id));
    }

    public List<LightResponse> findByRoom(Long roomId) {
        return repo.findByRoomId(roomId).stream().map(LightResponse::from).toList();
    }

    @Transactional
    public LightResponse create(DeviceRequest req) {
        checkUniqueness(req, null);
        Room room = roomService.getOrThrow(req.roomId());

        Light light = new Light();
        light.setName(req.name());
        light.setAddress(req.address());
        light.setRoom(room);
        light.setTopic(buildTopic("light", room.getName(), req.name()));
        return LightResponse.from(repo.save(light));
    }

    @Transactional
    public LightResponse update(Long id, DeviceRequest req) {
        Light light = getOrThrow(id);
        checkUniqueness(req, id);
        Room room = roomService.getOrThrow(req.roomId());

        light.setName(req.name());
        light.setAddress(req.address());
        light.setRoom(room);
        light.setTopic(buildTopic("light", room.getName(), req.name()));
        return LightResponse.from(repo.save(light));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new ResourceNotFoundException("Light", id);
        }
        repo.deleteById(id);
    }

    private Light getOrThrow(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Light", id));
    }

    private void checkUniqueness(DeviceRequest req, Long excludeId) {
        repo.findAll().stream()
                .filter(l -> excludeId == null || !l.getId().equals(excludeId))
                .forEach(l -> {
                    if (l.getName().equals(req.name()))
                        throw new DuplicateResourceException("Light with name '" + req.name() + "' already exists");
                    if (l.getAddress().equals(req.address()))
                        throw new DuplicateResourceException("Light with address '" + req.address() + "' already exists");
                });
    }

    static String buildTopic(String type, String roomName, String deviceName) {
        return type + "/" + roomName.toLowerCase().replace(' ', '_') + "/" + deviceName.toLowerCase().replace(' ', '_');
    }
}