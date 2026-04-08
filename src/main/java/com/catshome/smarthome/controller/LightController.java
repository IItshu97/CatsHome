package com.catshome.smarthome.controller;

import com.catshome.smarthome.dto.DeviceRequest;
import com.catshome.smarthome.dto.LightResponse;
import com.catshome.smarthome.service.LightService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/lights")
public class LightController {

    private final LightService service;

    public LightController(LightService service) {
        this.service = service;
    }

    @GetMapping
    public List<LightResponse> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public LightResponse getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/rooms/{roomId}")
    public List<LightResponse> getByRoom(@PathVariable Long roomId) {
        return service.findByRoom(roomId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LightResponse create(@Valid @RequestBody DeviceRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public LightResponse update(@PathVariable Long id, @Valid @RequestBody DeviceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
