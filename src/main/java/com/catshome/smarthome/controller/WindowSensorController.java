package com.catshome.smarthome.controller;

import com.catshome.smarthome.dto.DeviceRequest;
import com.catshome.smarthome.dto.WindowSensorResponse;
import com.catshome.smarthome.service.WindowSensorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/windows")
public class WindowSensorController {

    private final WindowSensorService service;

    public WindowSensorController(WindowSensorService service) {
        this.service = service;
    }

    @GetMapping
    public List<WindowSensorResponse> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public WindowSensorResponse getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/rooms/{roomId}")
    public List<WindowSensorResponse> getByRoom(@PathVariable Long roomId) {
        return service.findByRoom(roomId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WindowSensorResponse create(@Valid @RequestBody DeviceRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public WindowSensorResponse update(@PathVariable Long id, @Valid @RequestBody DeviceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
