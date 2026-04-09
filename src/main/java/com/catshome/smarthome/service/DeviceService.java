package com.catshome.smarthome.service;

import com.catshome.smarthome.dto.*;
import com.catshome.smarthome.entity.*;
import com.catshome.smarthome.exception.DuplicateResourceException;
import com.catshome.smarthome.exception.InvalidOperationException;
import com.catshome.smarthome.exception.ResourceNotFoundException;
import com.catshome.smarthome.mqtt.MqttPublisher;
import com.catshome.smarthome.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepo;
    private final SensorReadingStore influxReadingRepo;
    private final DeviceStateLogRepository stateLogRepo;
    private final RoomRepository roomRepo;
    private final MqttPublisher mqttPublisher;

    public DeviceService(DeviceRepository deviceRepo,
                         SensorReadingStore influxReadingRepo,
                         DeviceStateLogRepository stateLogRepo,
                         RoomRepository roomRepo,
                         MqttPublisher mqttPublisher) {
        this.deviceRepo = deviceRepo;
        this.influxReadingRepo = influxReadingRepo;
        this.stateLogRepo = stateLogRepo;
        this.roomRepo = roomRepo;
        this.mqttPublisher = mqttPublisher;
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

        if (deviceRepo.existsByDeviceTypeAndName(req.deviceType(), req.name())) {
            throw new DuplicateResourceException(
                    req.deviceType() + " device named '" + req.name() + "' already exists");
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
        device.setMqttTopic(req.deviceType().buildTopic(req.name()));
        return DeviceResponse.from(deviceRepo.save(device));
    }

    @Transactional
    public DeviceResponse update(Long id, DeviceUpdateRequest req) {
        Device device = getOrThrow(id);
        Room room = roomRepo.findById(req.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", req.roomId()));

        // Check global name uniqueness per type, excluding self
        boolean nameConflict = deviceRepo.existsByDeviceTypeAndName(device.getDeviceType(), req.name())
                && !device.getName().equals(req.name());
        if (nameConflict) {
            throw new DuplicateResourceException(
                    device.getDeviceType() + " device named '" + req.name() + "' already exists");
        }

        boolean ipConflict = deviceRepo.existsByIpAddress(req.ipAddress())
                && !device.getIpAddress().equals(req.ipAddress());
        if (ipConflict) {
            throw new DuplicateResourceException("Device with IP '" + req.ipAddress() + "' already registered");
        }

        device.setName(req.name());
        device.setRoom(room);
        device.setIpAddress(req.ipAddress());
        device.setMqttTopic(device.getDeviceType().buildTopic(req.name()));
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

    public List<SensorReadingPoint> getReadings(Long id, Instant from, Instant to) {
        Device device = getOrThrow(id);
        if (!device.getDeviceType().hasReadings()) {
            throw new InvalidOperationException(
                    device.getDeviceType() + " does not produce sensor readings");
        }
        return influxReadingRepo.findByDeviceIdBetween(id, from, to);
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
            case LIGHT -> {
                validateLightCommand(req.command(), device.isDimmer());
                // Firmware uses "1"/"0"; REST API uses "on"/"off"
                mqttPublisher.publish(device.getMqttTopic(), "on".equals(req.command()) ? "1" : "0");
            }
            case SHUTTER -> {
                validateShutterCommand(req.command());
                mqttPublisher.publish(device.getMqttTopic() + "/command", req.command());
            }
            default -> throw new InvalidOperationException(
                    device.getDeviceType() + " does not support /command");
        }
    }

    public void sendBrightness(Long id, BrightnessRequest req) {
        Device device = getOrThrow(id);
        if (device.getDeviceType() != DeviceType.LIGHT || !device.isDimmer()) {
            throw new InvalidOperationException("Only dimmer lights support /brightness");
        }
        mqttPublisher.publish(device.getMqttTopic(), String.valueOf(req.value()));
    }

    public void sendPosition(Long id, PositionRequest req) {
        Device device = getOrThrow(id);
        if (device.getDeviceType() != DeviceType.SHUTTER) {
            throw new InvalidOperationException("Only shutters support /position");
        }
        mqttPublisher.publish(device.getMqttTopic() + "/position", String.valueOf(req.position()));
    }

    public void sendThermostatSettings(Long id, ThermostatRequest req) {
        Device device = getOrThrow(id);
        if (device.getDeviceType() != DeviceType.THERMOSTAT) {
            throw new InvalidOperationException("Only thermostats support /thermostat");
        }
        String payload = "{\"target\":" + req.target() + ",\"mode\":\"" + req.mode() + "\"}";
        mqttPublisher.publish(device.getMqttTopic() + "/set", payload);
    }

    public void resetEnergy(Long id) {
        Device device = getOrThrow(id);
        if (device.getDeviceType() != DeviceType.ENERGY) {
            throw new InvalidOperationException("Only energy meters support /reset_energy");
        }
        mqttPublisher.publish(device.getMqttTopic() + "/reset_energy", "reset");
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