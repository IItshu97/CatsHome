package com.catshome.smarthome.integration;

import com.catshome.smarthome.AbstractContainerTest;
import com.catshome.smarthome.entity.Device;
import com.catshome.smarthome.entity.DeviceType;
import com.catshome.smarthome.entity.Room;
import com.catshome.smarthome.repository.DeviceRepository;
import com.catshome.smarthome.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.flyway.enabled=true")
@TestPropertySource(properties = "spring.test.database.replace=none")
class DeviceRepositoryIT extends AbstractContainerTest {

    @Autowired DeviceRepository deviceRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired TestEntityManager em;

    private Room living;
    private Room bedroom;

    @BeforeEach
    void setUp() {
        deviceRepo.deleteAll();
        roomRepo.deleteAll();
        living  = em.persistAndFlush(room("Living Room"));
        bedroom = em.persistAndFlush(room("Bedroom"));
    }

    // ── findWithFilters ───────────────────────────────────────────────────────

    @Test
    void findWithFilters_noFilters_returnsAll() {
        em.persistAndFlush(device("lamp",    DeviceType.LIGHT,  "10.0.0.1", living));
        em.persistAndFlush(device("door1",   DeviceType.DOOR,   "10.0.0.2", living));
        em.persistAndFlush(device("sensor1", DeviceType.TEMPERATURE, "10.0.0.3", bedroom));

        List<Device> result = deviceRepo.findWithFilters(null, null, null);
        assertEquals(3, result.size());
    }

    @Test
    void findWithFilters_byType() {
        em.persistAndFlush(device("lamp1", DeviceType.LIGHT, "10.0.0.1", living));
        em.persistAndFlush(device("lamp2", DeviceType.LIGHT, "10.0.0.2", bedroom));
        em.persistAndFlush(device("door1", DeviceType.DOOR,  "10.0.0.3", living));

        List<Device> lights = deviceRepo.findWithFilters(DeviceType.LIGHT, null, null);
        assertEquals(2, lights.size());
        assertTrue(lights.stream().allMatch(d -> d.getDeviceType() == DeviceType.LIGHT));
    }

    @Test
    void findWithFilters_byRoomId() {
        em.persistAndFlush(device("lamp1", DeviceType.LIGHT, "10.0.0.1", living));
        em.persistAndFlush(device("lamp2", DeviceType.LIGHT, "10.0.0.2", bedroom));
        em.persistAndFlush(device("door1", DeviceType.DOOR,  "10.0.0.3", living));

        List<Device> inLiving = deviceRepo.findWithFilters(null, living.getId(), null);
        assertEquals(2, inLiving.size());
        assertTrue(inLiving.stream().allMatch(d -> d.getRoom().getId().equals(living.getId())));
    }

    @Test
    void findWithFilters_byOnline() {
        Device onlineDevice = device("lamp1", DeviceType.LIGHT, "10.0.0.1", living);
        onlineDevice.setOnline(true);
        Device offlineDevice = device("lamp2", DeviceType.LIGHT, "10.0.0.2", bedroom);
        offlineDevice.setOnline(false);
        em.persistAndFlush(onlineDevice);
        em.persistAndFlush(offlineDevice);

        List<Device> online = deviceRepo.findWithFilters(null, null, true);
        assertEquals(1, online.size());
        assertTrue(online.get(0).isOnline());
    }

    @Test
    void findWithFilters_combinedTypeAndRoom() {
        em.persistAndFlush(device("lamp1", DeviceType.LIGHT, "10.0.0.1", living));
        em.persistAndFlush(device("lamp2", DeviceType.LIGHT, "10.0.0.2", bedroom));
        em.persistAndFlush(device("door1", DeviceType.DOOR,  "10.0.0.3", living));

        List<Device> result = deviceRepo.findWithFilters(DeviceType.LIGHT, living.getId(), null);
        assertEquals(1, result.size());
        assertEquals("lamp1", result.get(0).getName());
    }

    // ── existsByIpAddress ─────────────────────────────────────────────────────

    @Test
    void existsByIpAddress_trueForRegisteredIp() {
        em.persistAndFlush(device("lamp", DeviceType.LIGHT, "10.0.0.50", living));

        assertTrue(deviceRepo.existsByIpAddress("10.0.0.50"));
        assertFalse(deviceRepo.existsByIpAddress("10.0.0.51"));
    }

    // ── existsByDeviceTypeAndRoomIdAndName ────────────────────────────────────

    @Test
    void existsByTypeRoomName_trueForExactMatch() {
        em.persistAndFlush(device("lamp", DeviceType.LIGHT, "10.0.0.1", living));

        assertTrue(deviceRepo.existsByDeviceTypeAndRoomIdAndName(
                DeviceType.LIGHT, living.getId(), "lamp"));

        // Different type — false
        assertFalse(deviceRepo.existsByDeviceTypeAndRoomIdAndName(
                DeviceType.DOOR, living.getId(), "lamp"));

        // Different room — false
        assertFalse(deviceRepo.existsByDeviceTypeAndRoomIdAndName(
                DeviceType.LIGHT, bedroom.getId(), "lamp"));
    }

    // ── unique constraints ────────────────────────────────────────────────────

    @Test
    void uniqueConstraint_rejectsDeviceWithSameTypeRoomName() {
        em.persistAndFlush(device("lamp", DeviceType.LIGHT, "10.0.0.1", living));

        Device duplicate = device("lamp", DeviceType.LIGHT, "10.0.0.2", living);
        assertThrows(DataIntegrityViolationException.class,
                () -> em.persistAndFlush(duplicate));
    }

    @Test
    void uniqueConstraint_allowsSameNameDifferentType() {
        em.persistAndFlush(device("sensor1", DeviceType.DOOR,   "10.0.0.1", living));
        assertDoesNotThrow(() ->
                em.persistAndFlush(device("sensor1", DeviceType.WINDOW, "10.0.0.2", living)));
    }

    @Test
    void uniqueConstraint_rejectsDuplicateIpAddress() {
        em.persistAndFlush(device("lamp",  DeviceType.LIGHT, "10.0.0.99", living));
        Device dup = device("lamp2", DeviceType.LIGHT, "10.0.0.99", bedroom);
        assertThrows(DataIntegrityViolationException.class,
                () -> em.persistAndFlush(dup));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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