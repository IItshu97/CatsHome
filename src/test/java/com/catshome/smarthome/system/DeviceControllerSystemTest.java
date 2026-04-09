package com.catshome.smarthome.system;

import com.catshome.smarthome.AbstractContainerTest;
import com.catshome.smarthome.entity.Device;
import com.catshome.smarthome.entity.DeviceType;
import com.catshome.smarthome.entity.Room;
import com.catshome.smarthome.repository.DeviceRepository;
import com.catshome.smarthome.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class DeviceControllerSystemTest extends AbstractContainerTest {

    @Autowired MockMvc mvc;
    @Autowired DeviceRepository deviceRepo;
    @Autowired RoomRepository roomRepo;

    private long roomId;

    @BeforeEach
    void clean() {
        deviceRepo.deleteAll();
        roomRepo.deleteAll();
        Room room = new Room();
        room.setName("Test Room");
        roomId = roomRepo.save(room).getId();
    }

    // ── POST /devices ─────────────────────────────────────────────────────────

    @Test
    void registerDevice_201_withGeneratedTopic() throws Exception {
        mvc.perform(post("/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"lamp1","deviceType":"LIGHT","isDimmer":false,
                                 "roomId":%d,"ipAddress":"192.168.1.10"}
                                """.formatted(roomId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mqttTopic").value("light/lamp1"))
                .andExpect(jsonPath("$.deviceType").value("LIGHT"))
                .andExpect(jsonPath("$.isDimmer").value(false))
                .andExpect(jsonPath("$.online").value(false));
    }

    @Test
    void registerDevice_topicNormalizesNameSpaces() throws Exception {
        mvc.perform(post("/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Front Door","deviceType":"DOOR",
                                 "roomId":%d,"ipAddress":"192.168.1.20"}
                                """.formatted(roomId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mqttTopic").value("door/front_door"));
    }

    @Test
    void registerDevice_404_whenRoomNotFound() throws Exception {
        mvc.perform(post("/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"lamp","deviceType":"LIGHT",
                                 "roomId":9999,"ipAddress":"192.168.1.10"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void registerDevice_409_onDuplicateTypeRoomName() throws Exception {
        registerDeviceViaApi("lamp", "LIGHT", false, roomId, "192.168.1.10");

        mvc.perform(post("/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"lamp","deviceType":"LIGHT",
                                 "roomId":%d,"ipAddress":"192.168.1.11"}
                                """.formatted(roomId)))
                .andExpect(status().isConflict());
    }

    @Test
    void registerDevice_409_onDuplicateIp() throws Exception {
        registerDeviceViaApi("lamp1", "LIGHT", false, roomId, "192.168.1.10");

        mvc.perform(post("/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"lamp2","deviceType":"LIGHT",
                                 "roomId":%d,"ipAddress":"192.168.1.10"}
                                """.formatted(roomId)))
                .andExpect(status().isConflict());
    }

    @Test
    void registerDevice_400_whenIpInvalid() throws Exception {
        mvc.perform(post("/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"lamp","deviceType":"LIGHT",
                                 "roomId":%d,"ipAddress":"not-an-ip"}
                                """.formatted(roomId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ipAddress").exists());
    }

    @Test
    void registerDevice_400_whenRequiredFieldsMissing() throws Exception {
        mvc.perform(post("/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /devices ──────────────────────────────────────────────────────────

    @Test
    void listDevices_returnsAll() throws Exception {
        registerDeviceViaApi("lamp1", "LIGHT", false, roomId, "192.168.1.10");
        registerDeviceViaApi("door1", "DOOR",  false, roomId, "192.168.1.11");

        mvc.perform(get("/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listDevices_filterByType() throws Exception {
        registerDeviceViaApi("lamp1", "LIGHT", false, roomId, "192.168.1.10");
        registerDeviceViaApi("door1", "DOOR",  false, roomId, "192.168.1.11");

        mvc.perform(get("/devices?type=LIGHT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].deviceType").value("LIGHT"));
    }

    @Test
    void listDevices_filterByRoomId() throws Exception {
        Room other = new Room();
        other.setName("Other Room");
        long otherRoomId = roomRepo.save(other).getId();

        registerDeviceViaApi("lamp1", "LIGHT", false, roomId,      "192.168.1.10");
        registerDeviceViaApi("lamp2", "LIGHT", false, otherRoomId, "192.168.1.11");

        mvc.perform(get("/devices?roomId=" + roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].roomId").value(roomId));
    }

    @Test
    void listDevices_filterByOnline() throws Exception {
        String body = registerDeviceViaApi("lamp1", "LIGHT", false, roomId, "192.168.1.10");
        long id = extractId(body);

        // Set device online via repository directly
        deviceRepo.findById(id).ifPresent(d -> {
            d.setOnline(true);
            deviceRepo.save(d);
        });

        registerDeviceViaApi("door1", "DOOR", false, roomId, "192.168.1.11"); // stays offline

        mvc.perform(get("/devices?online=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].online").value(true));
    }

    // ── GET /devices/{id} ─────────────────────────────────────────────────────

    @Test
    void getDevice_returnsDevice() throws Exception {
        long id = extractId(registerDeviceViaApi("temp1", "TEMPERATURE", false, roomId, "192.168.1.10"));

        mvc.perform(get("/devices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("temp1"))
                .andExpect(jsonPath("$.deviceType").value("TEMPERATURE"));
    }

    @Test
    void getDevice_404_whenNotFound() throws Exception {
        mvc.perform(get("/devices/9999"))
                .andExpect(status().isNotFound());
    }

    // ── PUT /devices/{id} ─────────────────────────────────────────────────────

    @Test
    void updateDevice_updatesNameAndRegeneratesTopic() throws Exception {
        long id = extractId(registerDeviceViaApi("old", "LIGHT", false, roomId, "192.168.1.10"));

        mvc.perform(put("/devices/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"new_lamp","roomId":%d,"ipAddress":"192.168.1.10"}
                                """.formatted(roomId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new_lamp"))
                .andExpect(jsonPath("$.mqttTopic").value("light/new_lamp"));
    }

    @Test
    void updateDevice_404_whenNotFound() throws Exception {
        mvc.perform(put("/devices/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"x","roomId":%d,"ipAddress":"1.1.1.1"}
                                """.formatted(roomId)))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /devices/{id} ──────────────────────────────────────────────────

    @Test
    void deleteDevice_204() throws Exception {
        long id = extractId(registerDeviceViaApi("lamp", "LIGHT", false, roomId, "192.168.1.10"));

        mvc.perform(delete("/devices/" + id))
                .andExpect(status().isNoContent());

        mvc.perform(get("/devices/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDevice_404_whenNotFound() throws Exception {
        mvc.perform(delete("/devices/9999"))
                .andExpect(status().isNotFound());
    }

    // ── POST /devices/{id}/command ────────────────────────────────────────────

    @Test
    void command_422_whenDeviceTypeDoesNotSupportIt() throws Exception {
        long id = extractId(registerDeviceViaApi("door1", "DOOR", false, roomId, "192.168.1.10"));

        mvc.perform(post("/devices/" + id + "/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"command":"open"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("does not support /command")));
    }

    @Test
    void command_422_whenLightCommandIsInvalid() throws Exception {
        long id = extractId(registerDeviceViaApi("lamp1", "LIGHT", false, roomId, "192.168.1.10"));

        mvc.perform(post("/devices/" + id + "/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"command":"blink"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("'on' or 'off'")));
    }

    @Test
    void command_422_forDimmerRedirectsToBrightnessEndpoint() throws Exception {
        long id = extractId(registerDeviceViaApi("strip", "LIGHT", true, roomId, "192.168.1.10"));

        mvc.perform(post("/devices/" + id + "/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"command":"on"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("/brightness")));
    }

    @Test
    void command_400_whenBodyIsEmpty() throws Exception {
        long id = extractId(registerDeviceViaApi("lamp1", "LIGHT", false, roomId, "192.168.1.10"));

        mvc.perform(post("/devices/" + id + "/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /devices/{id}/brightness ─────────────────────────────────────────

    @Test
    void brightness_400_whenOutOfRange() throws Exception {
        long id = extractId(registerDeviceViaApi("strip", "LIGHT", true, roomId, "192.168.1.10"));

        mvc.perform(post("/devices/" + id + "/brightness")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":150}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void brightness_422_whenNotADimmer() throws Exception {
        long id = extractId(registerDeviceViaApi("lamp", "LIGHT", false, roomId, "192.168.1.10"));

        mvc.perform(post("/devices/" + id + "/brightness")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":75}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── GET /devices/alarms ───────────────────────────────────────────────────

    @Test
    void alarms_returnsOnlyActiveAlarmDevices() throws Exception {
        long smokeId = extractId(registerDeviceViaApi("smoke1", "SMOKE", false, roomId, "192.168.1.10"));
        long floodId = extractId(registerDeviceViaApi("flood1", "FLOOD", false, roomId, "192.168.1.11"));
        long lampId  = extractId(registerDeviceViaApi("lamp1",  "LIGHT", false, roomId, "192.168.1.12"));

        // Set smoke to alarm, flood to clear, lamp to on
        setStateJson(smokeId, "{\"value\":\"alarm\"}");
        setStateJson(floodId, "{\"value\":\"clear\"}");
        setStateJson(lampId,  "{\"value\":\"1\"}");

        mvc.perform(get("/devices/alarms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(smokeId));
    }

    @Test
    void alarms_emptyListWhenNoActiveAlarms() throws Exception {
        mvc.perform(get("/devices/alarms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String registerDeviceViaApi(String name, String type, boolean dimmer,
                                        long rId, String ip) throws Exception {
        return mvc.perform(post("/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","deviceType":"%s","isDimmer":%b,
                                 "roomId":%d,"ipAddress":"%s"}
                                """.formatted(name, type, dimmer, rId, ip)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private long extractId(String json) {
        return Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private void setStateJson(long deviceId, String stateJson) {
        deviceRepo.findById(deviceId).ifPresent(d -> {
            d.setStateJson(stateJson);
            deviceRepo.save(d);
        });
    }
}