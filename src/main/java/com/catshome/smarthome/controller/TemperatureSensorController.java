package com.catshome.smarthome.controller;

import com.catshome.smarthome.dto.DeviceRequest;
import com.catshome.smarthome.dto.TemperatureSensorResponse;
import com.catshome.smarthome.service.TemperatureSensorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/temperatures")
public class TemperatureSensorController {

    private final TemperatureSensorService service;

    public TemperatureSensorController(TemperatureSensorService service) {
        this.service = service;
    }

    @GetMapping
    public List<TemperatureSensorResponse> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public TemperatureSensorResponse getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/rooms/{roomId}")
    public List<TemperatureSensorResponse> getByRoom(@PathVariable Long roomId) {
        return service.findByRoom(roomId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemperatureSensorResponse create(@Valid @RequestBody DeviceRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public TemperatureSensorResponse update(@PathVariable Long id, @Valid @RequestBody DeviceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}