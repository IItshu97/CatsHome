package com.catshome.smarthome.integration;

import com.catshome.smarthome.AbstractContainerTest;
import com.catshome.smarthome.entity.Device;
import com.catshome.smarthome.entity.DeviceType;
import com.catshome.smarthome.entity.Room;
import com.catshome.smarthome.repository.DeviceRepository;
import com.catshome.smarthome.repository.DeviceStateLogRepository;
import com.catshome.smarthome.repository.RoomRepository;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Integration test: verifies end-to-end MQTT message processing.
 *
 * A HiveMQ CE container acts as the broker. The Spring context connects to it and
 * subscribes on startup. A test Paho client publishes messages; we assert that the
 * server has updated device state in PostgreSQL.
 */
@SpringBootTest
class MqttMessageHandlerIT extends AbstractContainerTest {

    static final HiveMQContainer HIVEMQ =
            new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce:latest"));

    static {
        HIVEMQ.start();
    }

    @DynamicPropertySource
    static void mqttProperties(DynamicPropertyRegistry registry) {
        registry.add("mqtt.broker-url",
                () -> "tcp://" + HIVEMQ.getHost() + ":" + HIVEMQ.getMqttPort());
        registry.add("mqtt.client-id", () -> "catshome-server-test");
    }

    @Autowired DeviceRepository deviceRepo;
    @Autowired RoomRepository roomRepo;
    @Autowired DeviceStateLogRepository stateLogRepo;

    private Room room;

    @BeforeEach
    void setUp() {
        stateLogRepo.deleteAll();
        deviceRepo.deleteAll();
        roomRepo.deleteAll();
        room = roomRepo.save(room("Test Room"));
    }

    // ── temperature sensor message ────────────────────────────────────────────

    @Test
    void whenTemperatureMessageReceived_thenStateJsonUpdated() throws Exception {
        Device sensor = deviceRepo.save(device("sensor", DeviceType.TEMPERATURE));

        publishViaPaho(sensor.getMqttTopic(),
                "{\"temperature\":22.5,\"humidity\":58.0}");

        await().atMost(3, SECONDS).untilAsserted(() -> {
            Device updated = deviceRepo.findById(sensor.getId()).orElseThrow();
            assertThat(updated.getStateJson()).contains("22.5").contains("58.0");
            assertThat(updated.getLastSeen()).isNotNull();
        });
    }

    // ── light relay state update + state change log ───────────────────────────

    @Test
    void whenLightStateMessageReceived_thenStateJsonAndStateLogUpdated() throws Exception {
        Device lamp = deviceRepo.save(device("lamp", DeviceType.LIGHT));

        publishViaPaho(lamp.getMqttTopic(), "1");

        await().atMost(3, SECONDS).untilAsserted(() -> {
            Device updated = deviceRepo.findById(lamp.getId()).orElseThrow();
            assertThat(updated.getStateJson()).contains("\"value\":\"1\"");
        });

        // Publish a second state to verify the state log records the transition
        publishViaPaho(lamp.getMqttTopic(), "0");

        await().atMost(3, SECONDS).untilAsserted(() -> {
            Device updated = deviceRepo.findById(lamp.getId()).orElseThrow();
            assertThat(updated.getStateJson()).contains("\"value\":\"0\"");
            assertThat(stateLogRepo.findAll())
                    .anyMatch(e -> "1".equals(e.getOldValue()) && "0".equals(e.getNewValue()));
        });
    }

    // ── online / offline status ───────────────────────────────────────────────

    @Test
    void whenOnlineStatusReceived_thenDeviceMarkedOnline() throws Exception {
        Device sensor = deviceRepo.save(device("door", DeviceType.DOOR));

        publishViaPaho(sensor.getMqttTopic() + "/status", "online");

        await().atMost(3, SECONDS).untilAsserted(() -> {
            Device updated = deviceRepo.findById(sensor.getId()).orElseThrow();
            assertThat(updated.isOnline()).isTrue();
        });
    }

    @Test
    void whenOfflineStatusReceived_thenDeviceMarkedOffline() throws Exception {
        Device sensor = deviceRepo.save(device("door", DeviceType.DOOR));

        publishViaPaho(sensor.getMqttTopic() + "/status", "online");
        await().atMost(3, SECONDS).until(() ->
                deviceRepo.findById(sensor.getId()).map(Device::isOnline).orElse(false));

        publishViaPaho(sensor.getMqttTopic() + "/status", "offline");

        await().atMost(3, SECONDS).untilAsserted(() -> {
            Device updated = deviceRepo.findById(sensor.getId()).orElseThrow();
            assertThat(updated.isOnline()).isFalse();
        });
    }

    // ── shutter state sub-topic ───────────────────────────────────────────────

    @Test
    void whenShutterStateMessageReceived_thenStateJsonUpdated() throws Exception {
        Device shutter = deviceRepo.save(device("roller", DeviceType.SHUTTER));

        publishViaPaho(shutter.getMqttTopic() + "/state",
                "{\"state\":\"moving\",\"position\":75}");

        await().atMost(3, SECONDS).untilAsserted(() -> {
            Device updated = deviceRepo.findById(shutter.getId()).orElseThrow();
            assertThat(updated.getStateJson()).contains("\"state\":\"moving\"").contains("75");
        });
    }

    // ── unknown topic is silently ignored ─────────────────────────────────────

    @Test
    void whenUnknownTopicReceived_noExceptionThrown() throws Exception {
        // Topic matches +/+/+ but no device registered — should log and move on
        publishViaPaho("light/999/nonexistent", "1");

        // No assertion needed; test passes if no exception surfaces
        Thread.sleep(300);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void publishViaPaho(String topic, String payload) throws Exception {
        String brokerUrl = "tcp://" + HIVEMQ.getHost() + ":" + HIVEMQ.getMqttPort();
        MqttClient testClient = new MqttClient(brokerUrl, "test-publisher-" + System.nanoTime());
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        testClient.connect(opts);
        MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        msg.setQos(1);
        testClient.publish(topic, msg);
        testClient.disconnect();
        testClient.close();
    }

    private Room room(String name) {
        Room r = new Room();
        r.setName(name);
        return r;
    }

    private Device device(String name, DeviceType type) {
        Device d = new Device();
        d.setName(name);
        d.setDeviceType(type);
        d.setIpAddress("192.168.1.1");
        d.setRoom(room);
        d.setMqttTopic(type.buildTopic(room.getId(), name));
        return d;
    }
}
