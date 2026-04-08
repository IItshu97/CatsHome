package com.catshome.smarthome.controller;

import com.catshome.smarthome.dto.*;
import com.catshome.smarthome.entity.DeviceStateLog;
import com.catshome.smarthome.entity.DeviceType;
import com.catshome.smarthome.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @GetMapping
    public List<DeviceResponse> getAll(
            @RequestParam(required = false) DeviceType type,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Boolean online) {
        return service.findAll(type, roomId, online);
    }

    @GetMapping("/{id}")
    public DeviceResponse getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceResponse register(@Valid @RequestBody DeviceRegistrationRequest request) {
        return service.register(request);
    }

    @PutMapping("/{id}")
    public DeviceResponse update(@PathVariable Long id, @Valid @RequestBody DeviceUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/command")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void command(@PathVariable Long id, @Valid @RequestBody CommandRequest request) {
        service.sendCommand(id, request);
    }

    @PostMapping("/{id}/brightness")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void brightness(@PathVariable Long id, @Valid @RequestBody BrightnessRequest request) {
        service.sendBrightness(id, request);
    }

    @PostMapping("/{id}/position")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void position(@PathVariable Long id, @Valid @RequestBody PositionRequest request) {
        service.sendPosition(id, request);
    }

    @PostMapping("/{id}/thermostat")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void thermostat(@PathVariable Long id, @Valid @RequestBody ThermostatRequest request) {
        service.sendThermostatSettings(id, request);
    }

    @PostMapping("/{id}/reset_energy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resetEnergy(@PathVariable Long id) {
        service.resetEnergy(id);
    }

    // ── History / readings ────────────────────────────────────────────────────

    @GetMapping("/{id}/history")
    public List<DeviceStateLog> getHistory(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.getHistory(id, from, to);
    }

    @GetMapping("/{id}/readings")
    public List<SensorReadingPoint> getReadings(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.getReadings(id, from, to);
    }

    // ── Alarms ────────────────────────────────────────────────────────────────

    @GetMapping("/alarms")
    public List<DeviceResponse> getActiveAlarms() {
        return service.getActiveAlarms();
    }
}
