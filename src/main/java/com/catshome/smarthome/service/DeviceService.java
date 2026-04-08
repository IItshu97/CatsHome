package com.catshome.smarthome.service;

import com.catshome.smarthome.dto.*;
import com.catshome.smarthome.entity.*;
import com.catshome.smarthome.exception.DuplicateResourceException;
import com.catshome.smarthome.exception.InvalidOperationException;
import com.catshome.smarthome.exception.ResourceNotFoundException;
import com.catshome.smarthome.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepo;
    private final SensorReadingRepository readingRepo;
    private final DeviceStateLogRepository stateLogRepo;
    private final RoomRepository roomRepo;

    public DeviceService(DeviceRepository deviceRepo,
                         SensorReadingRepository readingRepo,
                         DeviceStateLogRepository stateLogRepo,
                         RoomRepository roomRepo) {
        this.deviceRepo = deviceRepo;
        this.readingRepo = readingRepo;
        this.stateLogRepo = stateLogRepo;
        this.roomRepo = roomRepo;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public List<DeviceResponse> findAll(DeviceType type, Long roomId, Boolean online) {
        return deviceRepo.findWithFilters(type, roomId, online)
                .stream().map(DeviceResponse::from).toList();
    }

    public DeviceResponse findById(Long id) {
        return DeviceResponse.from(getOrThrow(id));
    }

    @Transactional
    public DeviceResponse register(DeviceRegistrationRequest req) {
        Room room = roomRepo.findById(req.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", req.roomId()));

        if (deviceRepo.existsByDeviceTypeAndRoomIdAndName(req.deviceType(), req.roomId(), req.name())) {
            throw new DuplicateResourceException(
                    req.deviceType() + " device named '" + req.name() + "' already exists in this room");
        }
        if (deviceRepo.existsByIpAddress(req.ipAddress())) {
            throw new DuplicateResourceException("Device with IP '" + req.ipAddress() + "' already registered");
        }

        Device device = new Device();
        device.setName(req.name());
        device.setDeviceType(req.deviceType());
        device.setDimmer(req.isDimmer() != null && req.isDimmer());
        device.setRoom(room);
        device.setIpAddress(req.ipAddress());
        device.setMqttTopic(req.deviceType().buildTopic(room.getId(), req.name()));
        return DeviceResponse.from(deviceRepo.save(device));
    }

    @Transactional
    public DeviceResponse update(Long id, DeviceUpdateRequest req) {
        Device device = getOrThrow(id);
        Room room = roomRepo.findById(req.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", req.roomId()));

        // Check name uniqueness within (type, room) excluding self
        boolean nameConflict = deviceRepo
                .existsByDeviceTypeAndRoomIdAndName(device.getDeviceType(), req.roomId(), req.name())
                && !(device.getRoom().getId().equals(req.roomId()) && device.getName().equals(req.name()));
        if (nameConflict) {
            throw new DuplicateResourceException(
                    device.getDeviceType() + " device named '" + req.name() + "' already exists in this room");
        }

        boolean ipConflict = deviceRepo.existsByIpAddress(req.ipAddress())
                && !device.getIpAddress().equals(req.ipAddress());
        if (ipConflict) {
            throw new DuplicateResourceException("Device with IP '" + req.ipAddress() + "' already registered");
        }

        device.setName(req.name());
        device.setRoom(room);
        device.setIpAddress(req.ipAddress());
        device.setMqttTopic(device.getDeviceType().buildTopic(room.getId(), req.name()));
        return DeviceResponse.from(deviceRepo.save(device));
    }

    @Transactional
    public void delete(Long id) {
        if (!deviceRepo.existsById(id)) throw new ResourceNotFoundException("Device", id);
        deviceRepo.deleteById(id);
    }

    // ── History / readings ────────────────────────────────────────────────────

    public List<DeviceStateLog> getHistory(Long id, Instant from, Instant to) {
        getOrThrow(id); // validate exists
        return stateLogRepo.findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(id, from, to);
    }

    public List<SensorReading> getReadings(Long id, Instant from, Instant to) {
        Device device = getOrThrow(id);
        if (!device.getDeviceType().hasReadings()) {
            throw new InvalidOperationException(
                    device.getDeviceType() + " does not produce sensor readings");
        }
        return readingRepo.findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(id, from, to);
    }

    // ── Alarms ────────────────────────────────────────────────────────────────

    public List<DeviceResponse> getActiveAlarms() {
        return deviceRepo.findWithFilters(null, null, null).stream()
                .filter(d -> d.getDeviceType().isPriorityAlarm())
                .filter(d -> d.getStateJson() != null && d.getStateJson().contains("\"alarm\""))
                .map(DeviceResponse::from)
                .toList();
    }

    // ── Commands (placeholder — requires MQTT integration) ───────────────────

    public void sendCommand(Long id, CommandRequest req) {
        Device device = getOrThrow(id);
        switch (device.getDeviceType()) {
            case LIGHT -> validateLightCommand(req.command(), device.isDimmer());
            case SHUTTER -> validateShutterCommand(req.command());
            default -> throw new InvalidOperationException(
                    device.getDeviceType() + " does not support /command");
        }
        // TODO: publish via MQTT when broker integration is added
        throw new InvalidOperationException("MQTT not yet integrated — command accepted but not published");
    }

    public void sendBrightness(Long id, BrightnessRequest req) {
        Device device = getOrThrow(id);
        if (device.getDeviceType() != DeviceType.LIGHT || !device.isDimmer()) {
            throw new InvalidOperationException("Only dimmer lights support /brightness");
        }
        // TODO: publish via MQTT
        throw new InvalidOperationException("MQTT not yet integrated");
    }

    public void sendPosition(Long id, PositionRequest req) {
        Device device = getOrThrow(id);
        if (device.getDeviceType() != DeviceType.SHUTTER) {
            throw new InvalidOperationException("Only shutters support /position");
        }
        // TODO: publish via MQTT
        throw new InvalidOperationException("MQTT not yet integrated");
    }

    public void sendThermostatSettings(Long id, ThermostatRequest req) {
        Device device = getOrThrow(id);
        if (device.getDeviceType() != DeviceType.THERMOSTAT) {
            throw new InvalidOperationException("Only thermostats support /thermostat");
        }
        // TODO: publish via MQTT
        throw new InvalidOperationException("MQTT not yet integrated");
    }

    public void resetEnergy(Long id) {
        Device device = getOrThrow(id);
        if (device.getDeviceType() != DeviceType.ENERGY) {
            throw new InvalidOperationException("Only energy meters support /reset_energy");
        }
        // TODO: publish via MQTT
        throw new InvalidOperationException("MQTT not yet integrated");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    Device getOrThrow(Long id) {
        return deviceRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Device", id));
    }

    private void validateLightCommand(String command, boolean isDimmer) {
        if (isDimmer) {
            // dimmers accept numeric strings 0-100 via /brightness endpoint
            throw new InvalidOperationException("Use /brightness endpoint for dimmer devices");
        }
        if (!command.equals("on") && !command.equals("off")) {
            throw new InvalidOperationException("Light command must be 'on' or 'off'");
        }
    }

    private void validateShutterCommand(String command) {
        if (!command.equals("open") && !command.equals("close") && !command.equals("stop")) {
            throw new InvalidOperationException("Shutter command must be 'open', 'close', or 'stop'");
        }
    }
}