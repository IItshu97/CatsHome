package com.catshome.smarthome.service;

import com.catshome.smarthome.dto.RoomRequest;
import com.catshome.smarthome.dto.RoomResponse;
import com.catshome.smarthome.entity.Room;
import com.catshome.smarthome.exception.DuplicateResourceException;
import com.catshome.smarthome.exception.ResourceNotFoundException;
import com.catshome.smarthome.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomService {

    private final RoomRepository repo;

    public RoomService(RoomRepository repo) {
        this.repo = repo;
    }

    public List<RoomResponse> findAll() {
        return repo.findAll().stream().map(RoomResponse::from).toList();
    }

    public RoomResponse findById(Long id) {
        return RoomResponse.from(getOrThrow(id));
    }

    @Transactional
    public RoomResponse create(RoomRequest req) {
        if (repo.existsByName(req.name())) {
            throw new DuplicateResourceException("Room with name '" + req.name() + "' already exists");
        }
        Room room = new Room();
        room.setName(req.name());
        return RoomResponse.from(repo.save(room));
    }

    @Transactional
    public RoomResponse update(Long id, RoomRequest req) {
        Room room = getOrThrow(id);
        repo.findByName(req.name())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Room with name '" + req.name() + "' already exists");
                });
        room.setName(req.name());
        return RoomResponse.from(repo.save(room));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("Room", id);
        repo.deleteById(id);
    }

    private Room getOrThrow(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Room", id));
    }
}