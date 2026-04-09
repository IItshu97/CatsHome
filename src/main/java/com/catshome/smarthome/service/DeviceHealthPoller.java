package com.catshome.smarthome.service;

import com.catshome.smarthome.entity.Device;
import com.catshome.smarthome.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Polls every registered device's {@code GET /health} endpoint on a fixed schedule.
 *
 * On success: updates {@code firmware_version}, confirms {@code online = true}, refreshes {@code last_seen}.
 * On failure: logs at DEBUG level and leaves {@code online} unchanged — the MQTT LWT
 *             ({@code +/+/+/status}) remains the authoritative source for online status.
 */
@Component
public class DeviceHealthPoller {

    private static final Logger log = LoggerFactory.getLogger(DeviceHealthPoller.class);

    private final DeviceRepository deviceRepo;
    private final DeviceHealthClient healthClient;

    public DeviceHealthPoller(DeviceRepository deviceRepo, DeviceHealthClient healthClient) {
        this.deviceRepo = deviceRepo;
        this.healthClient = healthClient;
    }

    @Scheduled(fixedDelayString = "${device-health.poll-interval-ms:60000}")
    @Transactional
    public void pollAll() {
        deviceRepo.findAll().forEach(this::pollDevice);
    }

    private void pollDevice(Device device) {
        healthClient.fetchHealth(device.getIpAddress()).ifPresent(health -> {
            device.setFirmwareVersion(health.firmware());
            device.setOnline(true);
            device.setLastSeen(Instant.now());
            deviceRepo.save(device);
            log.debug("Health OK — device {} '{}', firmware={}, mqtt_connected={}",
                    device.getId(), device.getName(), health.firmware(), health.mqttConnected());
        });
    }
}
