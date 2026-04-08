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
class SensorReadingRepositoryIT extends AbstractContainerTest {

    @Autowired SensorReadingRepository readingRepo;
    @Autowired DeviceStateLogRepository stateLogRepo;
    @Autowired DeviceRepository deviceRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired TestEntityManager em;

    private Device tempSensor;
    private Device doorSensor;

    @BeforeEach
    void setUp() {
        stateLogRepo.deleteAll();
        readingRepo.deleteAll();
        deviceRepo.deleteAll();
        roomRepo.deleteAll();

        Room room = em.persistAndFlush(room("Lab"));
        tempSensor = em.persistAndFlush(device("temp1", DeviceType.TEMPERATURE, "10.0.0.1", room));
        doorSensor = em.persistAndFlush(device("door1", DeviceType.DOOR,        "10.0.0.2", room));
    }

    // ── SensorReadingRepository ───────────────────────────────────────────────

    @Test
    void findReadings_returnsOnlyWithinTimeRange() {
        Instant now = Instant.now();
        persistReading(tempSensor, now.minus(2, ChronoUnit.HOURS), "{\"temperature\":20.0}");
        persistReading(tempSensor, now.minus(1, ChronoUnit.HOURS), "{\"temperature\":21.0}");
        persistReading(tempSensor, now.minus(30, ChronoUnit.MINUTES), "{\"temperature\":22.0}");

        Instant from = now.minus(90, ChronoUnit.MINUTES);
        Instant to   = now;
        List<SensorReading> result = readingRepo
                .findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(tempSensor.getId(), from, to);

        assertEquals(2, result.size());
    }

    @Test
    void findReadings_orderedNewestFirst() {
        Instant now = Instant.now();
        persistReading(tempSensor, now.minus(60, ChronoUnit.MINUTES), "{\"temperature\":19.0}");
        persistReading(tempSensor, now.minus(30, ChronoUnit.MINUTES), "{\"temperature\":21.0}");
        persistReading(tempSensor, now.minus(10, ChronoUnit.MINUTES), "{\"temperature\":23.0}");

        List<SensorReading> result = readingRepo
                .findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(
                        tempSensor.getId(), now.minus(2, ChronoUnit.HOURS), now);

        assertEquals(3, result.size());
        assertTrue(result.get(0).getTimestamp().isAfter(result.get(1).getTimestamp()));
        assertTrue(result.get(1).getTimestamp().isAfter(result.get(2).getTimestamp()));
    }

    @Test
    void findReadings_isolatedToRequestedDevice() {
        Device sensor2 = em.persistAndFlush(device("temp2", DeviceType.TEMPERATURE, "10.0.0.3",
                tempSensor.getRoom()));
        Instant now = Instant.now();
        persistReading(tempSensor, now.minus(10, ChronoUnit.MINUTES), "{\"temperature\":20.0}");
        persistReading(sensor2,    now.minus(5,  ChronoUnit.MINUTES), "{\"temperature\":25.0}");

        List<SensorReading> result = readingRepo
                .findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(
                        tempSensor.getId(), now.minus(1, ChronoUnit.HOURS), now);

        assertEquals(1, result.size());
        assertEquals(tempSensor.getId(), result.get(0).getDevice().getId());
    }

    // ── DeviceStateLogRepository ──────────────────────────────────────────────

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
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void persistReading(Device d, Instant ts, String payload) {
        SensorReading r = new SensorReading();
        r.setDevice(d);
        r.setTimestamp(ts);
        r.setPayload(payload);
        em.persistAndFlush(r);
    }

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