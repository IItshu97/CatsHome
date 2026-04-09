package com.catshome.smarthome.mqtt;

import com.catshome.smarthome.entity.Device;
import com.catshome.smarthome.entity.DeviceStateLog;
import com.catshome.smarthome.entity.DeviceType;
import com.catshome.smarthome.repository.DeviceRepository;
import com.catshome.smarthome.repository.DeviceStateLogRepository;
import com.catshome.smarthome.repository.SensorReadingStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Processes all inbound MQTT messages and updates device state in the database.
 *
 * Routing (per device_contract.md §1.4):
 *   3-segment topic  → device state update
 *   4-segment /status → online / offline
 *   4-segment /state  → shutter position state
 *   4-segment /raw    → smoke sensor raw ADC value
 */
@Component
public class MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    private final DeviceRepository deviceRepo;
    private final DeviceStateLogRepository stateLogRepo;
    private final SensorReadingStore sensorReadingStore;
    private final ObjectMapper objectMapper;

    public MqttMessageHandler(DeviceRepository deviceRepo,
                               DeviceStateLogRepository stateLogRepo,
                               SensorReadingStore sensorReadingStore,
                               ObjectMapper objectMapper) {
        this.deviceRepo = deviceRepo;
        this.stateLogRepo = stateLogRepo;
        this.sensorReadingStore = sensorReadingStore;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handle(String topic, String payload) {
        String[] segments = topic.split("/");
        switch (segments.length) {
            case 3 -> handleStateMessage(topic, payload);
            case 4 -> {
                String sub = segments[3];
                switch (sub) {
                    case "status" -> handleStatusMessage(segments, payload);
                    case "state"  -> handleShutterState(segments, payload);
                    case "raw"    -> handleSmokeRaw(segments, payload);
                    default       -> log.debug("Ignoring unknown sub-topic on: {}", topic);
                }
            }
            default -> log.debug("Ignoring MQTT topic with unexpected segment count: {}", topic);
        }
    }

    // ── 3-segment: main device state ─────────────────────────────────────────

    private void handleStateMessage(String topic, String payload) {
        deviceRepo.findByMqttTopic(topic).ifPresentOrElse(
                device -> processState(device, payload),
                () -> log.debug("No device registered for topic: {}", topic)
        );
    }

    private void processState(Device device, String payload) {
        Instant now = Instant.now();
        String newStateJson;
        try {
            newStateJson = buildStateJson(device.getDeviceType(), payload, device.getStateJson(), now);
        } catch (Exception e) {
            log.warn("Malformed payload for device {} ({}): {}", device.getId(), device.getDeviceType(), e.getMessage());
            return;
        }

        if (shouldLogStateChange(device.getDeviceType())) {
            String oldValue = extractValueField(device.getStateJson());
            String newValue = extractValueField(newStateJson);
            persistStateLog(device, oldValue, newValue, now);
        }

        if (device.getDeviceType().hasReadings()) {
            sensorReadingStore.save(
                    device.getId(),
                    device.getDeviceType().name(),
                    device.getRoom().getId(),
                    now,
                    payload);
        }

        if (device.getDeviceType().isPriorityAlarm() && payload.contains("alarm")) {
            log.warn("PRIORITY ALARM: device '{}' (id={}, type={}, room={}) — alarm state received!",
                    device.getName(), device.getId(), device.getDeviceType(), device.getRoom().getId());
            // TODO: FCM push notification (requirement N-03 / CTL-08)
        }

        device.setStateJson(newStateJson);
        device.setLastSeen(now);
        deviceRepo.save(device);
    }

    // ── 4-segment /status: online / offline ──────────────────────────────────

    private void handleStatusMessage(String[] segments, String payload) {
        String baseTopic = baseTopic(segments);
        deviceRepo.findByMqttTopic(baseTopic).ifPresent(device -> {
            boolean online = "online".equalsIgnoreCase(payload.trim());
            device.setOnline(online);
            device.setLastSeen(Instant.now());
            deviceRepo.save(device);
            log.debug("Device {} ({}) marked {}", device.getId(), device.getName(), online ? "online" : "offline");
        });
    }

    // ── 4-segment /state: shutter position ───────────────────────────────────

    private void handleShutterState(String[] segments, String payload) {
        String baseTopic = baseTopic(segments);
        deviceRepo.findByMqttTopic(baseTopic).ifPresent(device -> {
            if (device.getDeviceType() != DeviceType.SHUTTER) {
                log.debug("Received /state on non-shutter device: {}", baseTopic);
                return;
            }
            try {
                Instant now = Instant.now();
                Map<String, Object> incoming = objectMapper.readValue(payload, new TypeReference<>() {});
                Map<String, Object> state = new LinkedHashMap<>(incoming);
                state.put("updatedAt", now.toString());

                String oldShutterState = extractField(device.getStateJson(), "state");
                String newShutterState = String.valueOf(incoming.get("state"));
                persistStateLog(device, oldShutterState, newShutterState, now);

                device.setStateJson(objectMapper.writeValueAsString(state));
                device.setLastSeen(now);
                deviceRepo.save(device);
            } catch (Exception e) {
                log.warn("Failed to parse shutter /state payload: {}", e.getMessage());
            }
        });
    }

    // ── 4-segment /raw: smoke sensor raw ADC ─────────────────────────────────

    private void handleSmokeRaw(String[] segments, String payload) {
        String baseTopic = baseTopic(segments);
        deviceRepo.findByMqttTopic(baseTopic).ifPresent(device -> {
            if (device.getDeviceType() != DeviceType.SMOKE) return;
            try {
                double rawValue = Double.parseDouble(payload.trim());
                Map<String, Object> state = device.getStateJson() != null
                        ? objectMapper.readValue(device.getStateJson(), new TypeReference<>() {})
                        : new LinkedHashMap<>();
                state.put("raw", rawValue);
                state.put("updatedAt", Instant.now().toString());
                device.setStateJson(objectMapper.writeValueAsString(state));
                deviceRepo.save(device);
            } catch (Exception e) {
                log.warn("Failed to parse smoke /raw payload: {}", e.getMessage());
            }
        });
    }

    // ── State JSON construction ───────────────────────────────────────────────

    private String buildStateJson(DeviceType type, String payload, String existingStateJson, Instant now) throws Exception {
        Map<String, Object> state = new LinkedHashMap<>();

        switch (type) {
            case LIGHT, DOOR, WINDOW, MOTION, FLOOD -> {
                state.put("value", payload.trim());
                state.put("updatedAt", now.toString());
            }
            case SMOKE -> {
                // Preserve the raw field if already present
                if (existingStateJson != null) {
                    Map<String, Object> existing = objectMapper.readValue(existingStateJson, new TypeReference<>() {});
                    if (existing.containsKey("raw")) state.put("raw", existing.get("raw"));
                }
                state.put("value", payload.trim());
                state.put("updatedAt", now.toString());
            }
            case TEMPERATURE -> {
                Map<String, Object> parsed = objectMapper.readValue(payload, new TypeReference<>() {});
                state.put("temperature", parsed.get("temperature"));
                state.put("humidity", parsed.get("humidity"));
                state.put("updatedAt", now.toString());
            }
            case THERMOSTAT -> {
                Map<String, Object> parsed = objectMapper.readValue(payload, new TypeReference<>() {});
                state.put("target", parsed.get("target"));
                state.put("mode", parsed.get("mode"));
                state.put("currentTemp", parsed.get("current_temp"));
                state.put("currentHumidity", parsed.get("current_humidity"));
                state.put("updatedAt", now.toString());
            }
            case LUX -> {
                state.put("value", Integer.parseInt(payload.trim()));
                state.put("updatedAt", now.toString());
            }
            case ENERGY -> {
                Map<String, Object> parsed = objectMapper.readValue(payload, new TypeReference<>() {});
                state.put("voltage", parsed.get("voltage"));
                state.put("current", parsed.get("current"));
                state.put("power", parsed.get("power"));
                state.put("energy", parsed.get("energy"));
                state.put("frequency", parsed.get("frequency"));
                state.put("powerFactor", parsed.get("power_factor"));
                state.put("updatedAt", now.toString());
            }
            default -> {
                state.put("value", payload.trim());
                state.put("updatedAt", now.toString());
            }
        }

        return objectMapper.writeValueAsString(state);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean shouldLogStateChange(DeviceType type) {
        return switch (type) {
            case LIGHT, DOOR, WINDOW, MOTION, SMOKE, FLOOD -> true;
            default -> false;
        };
        // SHUTTER is handled separately in handleShutterState
    }

    private String extractValueField(String stateJson) {
        return extractField(stateJson, "value");
    }

    private String extractField(String stateJson, String fieldName) {
        if (stateJson == null) return null;
        try {
            Map<String, Object> state = objectMapper.readValue(stateJson, new TypeReference<>() {});
            Object val = state.get(fieldName);
            return val != null ? String.valueOf(val) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void persistStateLog(Device device, String oldValue, String newValue, Instant timestamp) {
        if (Objects.equals(oldValue, newValue)) return;
        DeviceStateLog entry = new DeviceStateLog();
        entry.setDevice(device);
        entry.setOldValue(oldValue);
        entry.setNewValue(newValue);
        entry.setTimestamp(timestamp);
        stateLogRepo.save(entry);
    }

    private static String baseTopic(String[] segments) {
        return segments[0] + "/" + segments[1] + "/" + segments[2];
    }
}
