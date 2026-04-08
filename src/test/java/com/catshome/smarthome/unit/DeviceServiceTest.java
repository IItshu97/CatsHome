package com.catshome.smarthome.unit;

import com.catshome.smarthome.dto.*;
import com.catshome.smarthome.entity.*;
import com.catshome.smarthome.exception.DuplicateResourceException;
import com.catshome.smarthome.exception.InvalidOperationException;
import com.catshome.smarthome.exception.ResourceNotFoundException;
import com.catshome.smarthome.repository.*;
import com.catshome.smarthome.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock DeviceRepository deviceRepo;
    @Mock SensorReadingRepository readingRepo;
    @Mock DeviceStateLogRepository stateLogRepo;
    @Mock RoomRepository roomRepo;

    @InjectMocks DeviceService service;

    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room();
        room.setId(3L);
        room.setName("Living Room");
    }

    // ── buildTopic (static helper) ────────────────────────────────────────────

    @Nested
    class BuildTopic {
        @Test
        void usesTypeRoomIdAndNormalizedName() {
            assertEquals("light/3/lampa_salon",
                    DeviceType.LIGHT.buildTopic(3L, "lampa salon"));
        }

        @Test
        void lowercasesName() {
            assertEquals("door/1/front_door",
                    DeviceType.DOOR.buildTopic(1L, "FRONT DOOR"));
        }

        @Test
        void replacesSpacesWithUnderscores() {
            assertEquals("temperature/2/sensor_a",
                    DeviceType.TEMPERATURE.buildTopic(2L, "sensor a"));
        }
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Nested
    class Register {
        @Test
        void happyPath_generatesTopicAndPersists() {
            when(roomRepo.findById(3L)).thenReturn(Optional.of(room));
            when(deviceRepo.existsByDeviceTypeAndRoomIdAndName(DeviceType.LIGHT, 3L, "lamp")).thenReturn(false);
            when(deviceRepo.existsByIpAddress("192.168.1.10")).thenReturn(false);
            when(deviceRepo.save(any())).thenAnswer(inv -> {
                Device d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            DeviceResponse result = service.register(
                    new DeviceRegistrationRequest("lamp", DeviceType.LIGHT, false, 3L, "192.168.1.10"));

            assertEquals("light/3/lamp", result.mqttTopic());
            assertEquals(DeviceType.LIGHT, result.deviceType());
            assertFalse(result.isDimmer());
            verify(deviceRepo).save(any());
        }

        @Test
        void isDimmer_null_treatedAsFalse() {
            when(roomRepo.findById(3L)).thenReturn(Optional.of(room));
            when(deviceRepo.existsByDeviceTypeAndRoomIdAndName(any(), any(), any())).thenReturn(false);
            when(deviceRepo.existsByIpAddress(any())).thenReturn(false);
            when(deviceRepo.save(any())).thenAnswer(inv -> {
                Device d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            DeviceResponse result = service.register(
                    new DeviceRegistrationRequest("dimmer1", DeviceType.LIGHT, null, 3L, "192.168.1.11"));

            assertFalse(result.isDimmer());
        }

        @Test
        void throwsWhenRoomNotFound() {
            when(roomRepo.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    service.register(new DeviceRegistrationRequest("x", DeviceType.DOOR, null, 99L, "1.1.1.1")));
            verify(deviceRepo, never()).save(any());
        }

        @Test
        void throwsOnDuplicateTypeRoomName() {
            when(roomRepo.findById(3L)).thenReturn(Optional.of(room));
            when(deviceRepo.existsByDeviceTypeAndRoomIdAndName(DeviceType.LIGHT, 3L, "lamp")).thenReturn(true);

            assertThrows(DuplicateResourceException.class, () ->
                    service.register(new DeviceRegistrationRequest("lamp", DeviceType.LIGHT, false, 3L, "1.2.3.4")));
            verify(deviceRepo, never()).save(any());
        }

        @Test
        void throwsOnDuplicateIpAddress() {
            when(roomRepo.findById(3L)).thenReturn(Optional.of(room));
            when(deviceRepo.existsByDeviceTypeAndRoomIdAndName(any(), any(), any())).thenReturn(false);
            when(deviceRepo.existsByIpAddress("192.168.1.10")).thenReturn(true);

            assertThrows(DuplicateResourceException.class, () ->
                    service.register(new DeviceRegistrationRequest("lamp2", DeviceType.LIGHT, false, 3L, "192.168.1.10")));
        }
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Nested
    class Update {
        private Device existingDevice;

        @BeforeEach
        void setUp() {
            existingDevice = device(5L, "lamp", DeviceType.LIGHT, "192.168.1.10", room);
        }

        @Test
        void happyPath_updatesTopicOnRoomChange() {
            Room newRoom = new Room();
            newRoom.setId(7L);
            newRoom.setName("Bedroom");

            when(deviceRepo.findById(5L)).thenReturn(Optional.of(existingDevice));
            when(roomRepo.findById(7L)).thenReturn(Optional.of(newRoom));
            when(deviceRepo.existsByDeviceTypeAndRoomIdAndName(DeviceType.LIGHT, 7L, "lamp")).thenReturn(false);
            when(deviceRepo.existsByIpAddress("192.168.1.10")).thenReturn(true); // same IP, not a conflict
            when(deviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DeviceResponse result = service.update(5L,
                    new DeviceUpdateRequest("lamp", 7L, "192.168.1.10"));

            assertEquals("light/7/lamp", result.mqttTopic());
        }

        @Test
        void keepingOwnNameDoesNotThrow() {
            when(deviceRepo.findById(5L)).thenReturn(Optional.of(existingDevice));
            when(roomRepo.findById(3L)).thenReturn(Optional.of(room));
            // same (type, room, name) exists but it IS self
            when(deviceRepo.existsByDeviceTypeAndRoomIdAndName(DeviceType.LIGHT, 3L, "lamp")).thenReturn(true);
            when(deviceRepo.existsByIpAddress("192.168.1.10")).thenReturn(true);
            when(deviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() ->
                    service.update(5L, new DeviceUpdateRequest("lamp", 3L, "192.168.1.10")));
        }

        @Test
        void throwsWhenNameTakenByAnotherDevice() {
            when(deviceRepo.findById(5L)).thenReturn(Optional.of(existingDevice));
            when(roomRepo.findById(3L)).thenReturn(Optional.of(room));
            // "other_lamp" exists but device's own name is "lamp" — not self
            Device other = device(6L, "lamp2", DeviceType.LIGHT, "192.168.1.20", room);
            when(deviceRepo.existsByDeviceTypeAndRoomIdAndName(DeviceType.LIGHT, 3L, "lamp2")).thenReturn(true);

            assertThrows(DuplicateResourceException.class, () ->
                    service.update(5L, new DeviceUpdateRequest("lamp2", 3L, "192.168.1.10")));
        }

        @Test
        void throwsWhenIpTakenByAnotherDevice() {
            when(deviceRepo.findById(5L)).thenReturn(Optional.of(existingDevice));
            when(roomRepo.findById(3L)).thenReturn(Optional.of(room));
            when(deviceRepo.existsByDeviceTypeAndRoomIdAndName(any(), any(), any())).thenReturn(false);
            when(deviceRepo.existsByIpAddress("192.168.1.99")).thenReturn(true);

            assertThrows(DuplicateResourceException.class, () ->
                    service.update(5L, new DeviceUpdateRequest("lamp", 3L, "192.168.1.99")));
        }

        @Test
        void throwsWhenDeviceNotFound() {
            when(deviceRepo.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    service.update(99L, new DeviceUpdateRequest("x", 3L, "1.1.1.1")));
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_callsRepository() {
        when(deviceRepo.existsById(1L)).thenReturn(true);
        service.delete(1L);
        verify(deviceRepo).deleteById(1L);
    }

    @Test
    void delete_throwsWhenNotFound() {
        when(deviceRepo.existsById(99L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.delete(99L));
    }

    // ── getReadings ───────────────────────────────────────────────────────────

    @Test
    void getReadings_throwsForNonReadingsDevice() {
        Device door = device(1L, "front", DeviceType.DOOR, "1.1.1.1", room);
        when(deviceRepo.findById(1L)).thenReturn(Optional.of(door));

        assertThrows(InvalidOperationException.class, () ->
                service.getReadings(1L, Instant.now().minusSeconds(60), Instant.now()));
    }

    @Test
    void getReadings_delegatesToRepoForTemperatureSensor() {
        Device sensor = device(2L, "temp1", DeviceType.TEMPERATURE, "1.1.1.2", room);
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        when(deviceRepo.findById(2L)).thenReturn(Optional.of(sensor));
        when(readingRepo.findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(2L, from, to))
                .thenReturn(List.of());

        List<SensorReading> result = service.getReadings(2L, from, to);

        assertTrue(result.isEmpty());
        verify(readingRepo).findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(2L, from, to);
    }

    // ── getActiveAlarms ───────────────────────────────────────────────────────

    @Test
    void getActiveAlarms_returnsOnlyAlarmingPriorityDevices() {
        Device smokeActive = device(1L, "smoke1", DeviceType.SMOKE, "1.1.1.1", room);
        smokeActive.setStateJson("{\"value\":\"alarm\"}");

        Device smokeClear = device(2L, "smoke2", DeviceType.SMOKE, "1.1.1.2", room);
        smokeClear.setStateJson("{\"value\":\"clear\"}");

        Device floodActive = device(3L, "flood1", DeviceType.FLOOD, "1.1.1.3", room);
        floodActive.setStateJson("{\"value\":\"alarm\"}");

        Device lightOn = device(4L, "lamp", DeviceType.LIGHT, "1.1.1.4", room);
        lightOn.setStateJson("{\"value\":\"1\"}");

        when(deviceRepo.findWithFilters(null, null, null))
                .thenReturn(List.of(smokeActive, smokeClear, floodActive, lightOn));

        List<DeviceResponse> alarms = service.getActiveAlarms();

        assertEquals(2, alarms.size());
        assertTrue(alarms.stream().anyMatch(d -> d.id().equals(1L)));
        assertTrue(alarms.stream().anyMatch(d -> d.id().equals(3L)));
    }

    @Test
    void getActiveAlarms_emptyWhenNoActiveAlarms() {
        Device smokeClear = device(1L, "smoke1", DeviceType.SMOKE, "1.1.1.1", room);
        smokeClear.setStateJson("{\"value\":\"clear\"}");

        when(deviceRepo.findWithFilters(null, null, null)).thenReturn(List.of(smokeClear));

        assertTrue(service.getActiveAlarms().isEmpty());
    }

    // ── sendCommand ───────────────────────────────────────────────────────────

    @Nested
    class SendCommand {
        @ParameterizedTest
        @ValueSource(strings = {"on", "off"})
        void relayLight_validCommands_throwsMqttNotIntegrated(String command) {
            Device relay = device(1L, "lamp", DeviceType.LIGHT, "1.1.1.1", room);
            relay.setDimmer(false);
            when(deviceRepo.findById(1L)).thenReturn(Optional.of(relay));

            InvalidOperationException ex = assertThrows(InvalidOperationException.class,
                    () -> service.sendCommand(1L, new CommandRequest(command)));
            assertTrue(ex.getMessage().contains("MQTT not yet integrated"));
        }

        @Test
        void relayLight_invalidCommand_throwsValidationError() {
            Device relay = device(1L, "lamp", DeviceType.LIGHT, "1.1.1.1", room);
            relay.setDimmer(false);
            when(deviceRepo.findById(1L)).thenReturn(Optional.of(relay));

            InvalidOperationException ex = assertThrows(InvalidOperationException.class,
                    () -> service.sendCommand(1L, new CommandRequest("toggle")));
            assertTrue(ex.getMessage().contains("'on' or 'off'"));
        }

        @Test
        void dimmerLight_commandRejectedInFavorOfBrightnessEndpoint() {
            Device dimmer = device(1L, "strip", DeviceType.LIGHT, "1.1.1.1", room);
            dimmer.setDimmer(true);
            when(deviceRepo.findById(1L)).thenReturn(Optional.of(dimmer));

            InvalidOperationException ex = assertThrows(InvalidOperationException.class,
                    () -> service.sendCommand(1L, new CommandRequest("on")));
            assertTrue(ex.getMessage().contains("/brightness"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"open", "close", "stop"})
        void shutter_validCommands_throwsMqttNotIntegrated(String command) {
            Device shutter = device(1L, "roller", DeviceType.SHUTTER, "1.1.1.1", room);
            when(deviceRepo.findById(1L)).thenReturn(Optional.of(shutter));

            InvalidOperationException ex = assertThrows(InvalidOperationException.class,
                    () -> service.sendCommand(1L, new CommandRequest(command)));
            assertTrue(ex.getMessage().contains("MQTT not yet integrated"));
        }

        @Test
        void shutter_invalidCommand_throwsValidationError() {
            Device shutter = device(1L, "roller", DeviceType.SHUTTER, "1.1.1.1", room);
            when(deviceRepo.findById(1L)).thenReturn(Optional.of(shutter));

            InvalidOperationException ex = assertThrows(InvalidOperationException.class,
                    () -> service.sendCommand(1L, new CommandRequest("raise")));
            assertTrue(ex.getMessage().contains("'open', 'close', or 'stop'"));
        }

        @Test
        void nonCommandDevice_throwsUnsupported() {
            Device door = device(1L, "door1", DeviceType.DOOR, "1.1.1.1", room);
            when(deviceRepo.findById(1L)).thenReturn(Optional.of(door));

            InvalidOperationException ex = assertThrows(InvalidOperationException.class,
                    () -> service.sendCommand(1L, new CommandRequest("open")));
            assertTrue(ex.getMessage().contains("does not support /command"));
        }
    }

    // ── sendBrightness ────────────────────────────────────────────────────────

    @Test
    void sendBrightness_nonDimmerThrows() {
        Device relay = device(1L, "lamp", DeviceType.LIGHT, "1.1.1.1", room);
        relay.setDimmer(false);
        when(deviceRepo.findById(1L)).thenReturn(Optional.of(relay));

        assertThrows(InvalidOperationException.class,
                () -> service.sendBrightness(1L, new BrightnessRequest(75)));
    }

    @Test
    void sendBrightness_nonLightDeviceThrows() {
        Device door = device(1L, "door1", DeviceType.DOOR, "1.1.1.1", room);
        when(deviceRepo.findById(1L)).thenReturn(Optional.of(door));

        assertThrows(InvalidOperationException.class,
                () -> service.sendBrightness(1L, new BrightnessRequest(50)));
    }

    // ── sendPosition ──────────────────────────────────────────────────────────

    @Test
    void sendPosition_nonShutterThrows() {
        Device light = device(1L, "lamp", DeviceType.LIGHT, "1.1.1.1", room);
        when(deviceRepo.findById(1L)).thenReturn(Optional.of(light));

        assertThrows(InvalidOperationException.class,
                () -> service.sendPosition(1L, new PositionRequest(50)));
    }

    // ── sendThermostatSettings ────────────────────────────────────────────────

    @Test
    void sendThermostatSettings_nonThermostatThrows() {
        Device sensor = device(1L, "temp1", DeviceType.TEMPERATURE, "1.1.1.1", room);
        when(deviceRepo.findById(1L)).thenReturn(Optional.of(sensor));

        assertThrows(InvalidOperationException.class,
                () -> service.sendThermostatSettings(1L, new ThermostatRequest(21.0, "heat")));
    }

    // ── resetEnergy ───────────────────────────────────────────────────────────

    @Test
    void resetEnergy_nonEnergyMeterThrows() {
        Device light = device(1L, "lamp", DeviceType.LIGHT, "1.1.1.1", room);
        when(deviceRepo.findById(1L)).thenReturn(Optional.of(light));

        assertThrows(InvalidOperationException.class, () -> service.resetEnergy(1L));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Device device(Long id, String name, DeviceType type, String ip, Room r) {
        Device d = new Device();
        d.setId(id);
        d.setName(name);
        d.setDeviceType(type);
        d.setIpAddress(ip);
        d.setRoom(r);
        d.setMqttTopic(type.buildTopic(r.getId(), name));
        return d;
    }
}
