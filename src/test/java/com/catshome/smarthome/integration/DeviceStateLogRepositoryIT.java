package com.catshome.smarthome.integration;

import com.catshome.smarthome.AbstractContainerTest;
import com.catshome.smarthome.entity.*;
import com.catshome.smarthome.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.flyway.enabled=true")
@TestPropertySource(properties = "spring.test.database.replace=none")
class DeviceStateLogRepositoryIT extends AbstractContainerTest {

    @Autowired DeviceStateLogRepository stateLogRepo;
    @Autowired DeviceRepository deviceRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired TestEntityManager em;

    private Device doorSensor;

    @BeforeEach
    void setUp() {
        stateLogRepo.deleteAll();
        deviceRepo.deleteAll();
        roomRepo.deleteAll();

        Room room = em.persistAndFlush(room("Lab"));
        doorSensor = em.persistAndFlush(device("door1", DeviceType.DOOR, "10.0.0.1", room));
    }

    @Test
    void findStateLog_returnsOnlyWithinTimeRange() {
        Instant now = Instant.now();
        persistStateLog(doorSensor, now.minus(3, ChronoUnit.HOURS), "closed", "open");
        persistStateLog(doorSensor, now.minus(1, ChronoUnit.HOURS), "open",   "closed");
        persistStateLog(doorSensor, now.minus(10, ChronoUnit.MINUTES), "closed", "open");

        List<DeviceStateLog> result = stateLogRepo
                .findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(
                        doorSensor.getId(),
                        now.minus(2, ChronoUnit.HOURS),
                        now);

        assertEquals(2, result.size());
    }

    @Test
    void findStateLog_orderedNewestFirst() {
        Instant now = Instant.now();
        persistStateLog(doorSensor, now.minus(60, ChronoUnit.MINUTES), "closed", "open");
        persistStateLog(doorSensor, now.minus(30, ChronoUnit.MINUTES), "open",   "closed");
        persistStateLog(doorSensor, now.minus(5,  ChronoUnit.MINUTES), "closed", "open");

        List<DeviceStateLog> result = stateLogRepo
                .findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(
                        doorSensor.getId(),
                        now.minus(2, ChronoUnit.HOURS),
                        now);

        assertEquals(3, result.size());
        assertTrue(result.get(0).getTimestamp().isAfter(result.get(1).getTimestamp()));
        assertTrue(result.get(1).getTimestamp().isAfter(result.get(2).getTimestamp()));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void persistStateLog(Device d, Instant ts, String oldVal, String newVal) {
        DeviceStateLog log = new DeviceStateLog();
        log.setDevice(d);
        log.setTimestamp(ts);
        log.setOldValue(oldVal);
        log.setNewValue(newVal);
        em.persistAndFlush(log);
    }

    private Room room(String name) {
        Room r = new Room();
        r.setName(name);
        return r;
    }

    private Device device(String name, DeviceType type, String ip, Room r) {
        Device d = new Device();
        d.setName(name);
        d.setDeviceType(type);
        d.setIpAddress(ip);
        d.setRoom(r);
        d.setMqttTopic(type.topicPrefix() + "/" + r.getId() + "/" + name);
        return d;
    }
}