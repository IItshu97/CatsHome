package com.catshome.smarthome.controller;

import com.catshome.smarthome.dto.DeviceRequest;
import com.catshome.smarthome.dto.DoorSensorResponse;
import com.catshome.smarthome.service.DoorSensorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/doors")
public class DoorSensorController {

    private final DoorSensorService service;

    public DoorSensorController(DoorSensorService service) {
        this.service = service;
    }

    @GetMapping
    public List<DoorSensorResponse> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public DoorSensorResponse getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/rooms/{roomId}")
    public List<DoorSensorResponse> getByRoom(@PathVariable Long roomId) {
        return service.findByRoom(roomId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DoorSensorResponse create(@Valid @RequestBody DeviceRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public DoorSensorResponse update(@PathVariable Long id, @Valid @RequestBody DeviceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
