package com.catshome.smarthome.unit;

import com.catshome.smarthome.dto.DeviceHealthResponse;
import com.catshome.smarthome.entity.Device;
import com.catshome.smarthome.entity.DeviceType;
import com.catshome.smarthome.entity.Room;
import com.catshome.smarthome.repository.DeviceRepository;
import com.catshome.smarthome.service.DeviceHealthClient;
import com.catshome.smarthome.service.DeviceHealthPoller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceHealthPollerTest {

    @Mock DeviceRepository deviceRepo;
    @Mock DeviceHealthClient healthClient;

    @InjectMocks DeviceHealthPoller poller;

    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room();
        room.setId(1L);
        room.setName("Living Room");
    }

    @Test
    void pollAll_healthOk_updatesFirmwareVersionAndOnline() {
        Device device = device(1L, "lamp", "192.168.1.10");
        device.setFirmwareVersion(null);
        device.setOnline(false);
        when(deviceRepo.findAll()).thenReturn(List.of(device));
        when(healthClient.fetchHealth("192.168.1.10")).thenReturn(Optional.of(
                health("1.2.0", true)));

        poller.pollAll();

        ArgumentCaptor<Device> saved = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepo).save(saved.capture());
        assertThat(saved.getValue().getFirmwareVersion()).isEqualTo("1.2.0");
        assertThat(saved.getValue().isOnline()).isTrue();
        assertThat(saved.getValue().getLastSeen()).isNotNull();
    }

    @Test
    void pollAll_healthFails_deviceNotSaved() {
        Device device = device(1L, "lamp", "192.168.1.10");
        when(deviceRepo.findAll()).thenReturn(List.of(device));
        when(healthClient.fetchHealth("192.168.1.10")).thenReturn(Optional.empty());

        poller.pollAll();

        verify(deviceRepo, never()).save(any());
    }

    @Test
    void pollAll_multipleDevices_eachPolledIndependently() {
        Device d1 = device(1L, "lamp", "192.168.1.10");
        Device d2 = device(2L, "sensor", "192.168.1.11");
        when(deviceRepo.findAll()).thenReturn(List.of(d1, d2));
        when(healthClient.fetchHealth("192.168.1.10")).thenReturn(Optional.of(health("1.0.0", true)));
        when(healthClient.fetchHealth("192.168.1.11")).thenReturn(Optional.empty()); // unreachable

        poller.pollAll();

        verify(deviceRepo, times(1)).save(any()); // only d1 saved
        verify(healthClient).fetchHealth("192.168.1.10");
        verify(healthClient).fetchHealth("192.168.1.11");
    }

    @Test
    void pollAll_noDevices_nothingHappens() {
        when(deviceRepo.findAll()).thenReturn(List.of());

        poller.pollAll();

        verify(healthClient, never()).fetchHealth(any());
        verify(deviceRepo, never()).save(any());
    }

    @Test
    void pollAll_healthOk_firmwareVersionOverwritesPreviousValue() {
        Device device = device(1L, "lamp", "192.168.1.10");
        device.setFirmwareVersion("1.0.0");
        when(deviceRepo.findAll()).thenReturn(List.of(device));
        when(healthClient.fetchHealth("192.168.1.10")).thenReturn(Optional.of(health("1.1.0", true)));

        poller.pollAll();

        ArgumentCaptor<Device> saved = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepo).save(saved.capture());
        assertThat(saved.getValue().getFirmwareVersion()).isEqualTo("1.1.0");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Device device(Long id, String name, String ip) {
        Device d = new Device();
        d.setId(id);
        d.setName(name);
        d.setDeviceType(DeviceType.LIGHT);
        d.setIpAddress(ip);
        d.setRoom(room);
        d.setMqttTopic(DeviceType.LIGHT.buildTopic(room.getId(), name));
        return d;
    }

    private DeviceHealthResponse health(String firmware, boolean mqttConnected) {
        return new DeviceHealthResponse("light", firmware, 3600000L, -62, "HomeNet",
                "192.168.1.10", mqttConnected, "light/1/lamp");
    }
}