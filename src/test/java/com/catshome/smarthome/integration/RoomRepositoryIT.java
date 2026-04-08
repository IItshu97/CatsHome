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
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.flyway.enabled=true")
@TestPropertySource(properties = "spring.test.database.replace=none")
class RoomRepositoryIT extends AbstractContainerTest {

    @Autowired RoomRepository roomRepo;
    @Autowired DeviceRepository deviceRepo;
    @Autowired TestEntityManager em;

    @BeforeEach
    void clean() {
        deviceRepo.deleteAll();
        roomRepo.deleteAll();
    }

    @Test
    void existsByName_trueForExistingRoom() {
        persist(room("Kitchen"));

        assertTrue(roomRepo.existsByName("Kitchen"));
        assertFalse(roomRepo.existsByName("Bedroom"));
    }

    @Test
    void findByName_returnsRoomWhenPresent() {
        persist(room("Bathroom"));

        Optional<Room> found = roomRepo.findByName("Bathroom");

        assertTrue(found.isPresent());
        assertEquals("Bathroom", found.get().getName());
    }

    @Test
    void findByName_emptyForMissingRoom() {
        assertTrue(roomRepo.findByName("Nonexistent").isEmpty());
    }

    @Test
    void deleteRoom_cascadeDeletesDevices() {
        Room r = persist(room("Hall"));
        persist(device("sensor1", DeviceType.DOOR, "10.0.0.1", r));
        persist(device("sensor2", DeviceType.DOOR, "10.0.0.2", r));
        em.flush();

        assertEquals(2, deviceRepo.count());

        roomRepo.deleteById(r.getId());
        em.flush();
        em.clear();

        assertEquals(0, deviceRepo.count());
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

    private <T> T persist(T entity) {
        return em.persistAndFlush(entity);
    }
}
