package com.catshome.smarthome.system;

import com.catshome.smarthome.AbstractContainerTest;
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
class RoomControllerSystemTest extends AbstractContainerTest {

    @Autowired MockMvc mvc;
    @Autowired RoomRepository roomRepo;
    @Autowired DeviceRepository deviceRepo;

    @BeforeEach
    void clean() {
        deviceRepo.deleteAll();
        roomRepo.deleteAll();
    }

    // ── POST /rooms ───────────────────────────────────────────────────────────

    @Test
    void createRoom_returns201WithBody() throws Exception {
        mvc.perform(post("/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Kitchen"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Kitchen"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createRoom_400_whenNameIsBlank() throws Exception {
        mvc.perform(post("/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void createRoom_400_whenBodyMissesNameField() throws Exception {
        mvc.perform(post("/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRoom_409_onDuplicateName() throws Exception {
        mvc.perform(post("/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bathroom"}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(post("/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bathroom"}
                                """))
                .andExpect(status().isConflict());
    }

    // ── GET /rooms ────────────────────────────────────────────────────────────

    @Test
    void listRooms_returnsAll() throws Exception {
        createRoomViaApi("Living Room");
        createRoomViaApi("Bedroom");

        mvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listRooms_emptyList() throws Exception {
        mvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── GET /rooms/{id} ───────────────────────────────────────────────────────

    @Test
    void getRoom_returnsRoom() throws Exception {
        long id = extractId(createRoomViaApi("Office"));

        mvc.perform(get("/rooms/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Office"));
    }

    @Test
    void getRoom_404_whenNotFound() throws Exception {
        mvc.perform(get("/rooms/9999"))
                .andExpect(status().isNotFound());
    }

    // ── PUT /rooms/{id} ───────────────────────────────────────────────────────

    @Test
    void updateRoom_renames() throws Exception {
        long id = extractId(createRoomViaApi("Old Name"));

        mvc.perform(put("/rooms/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "New Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void updateRoom_404_whenNotFound() throws Exception {
        mvc.perform(put("/rooms/9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Anything"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRoom_409_whenNameTakenByOtherRoom() throws Exception {
        createRoomViaApi("Garage");
        long id = extractId(createRoomViaApi("Storage"));

        mvc.perform(put("/rooms/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Garage"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void updateRoom_allowsKeepingOwnName() throws Exception {
        long id = extractId(createRoomViaApi("Hallway"));

        mvc.perform(put("/rooms/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Hallway"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hallway"));
    }

    // ── DELETE /rooms/{id} ────────────────────────────────────────────────────

    @Test
    void deleteRoom_returns204() throws Exception {
        long id = extractId(createRoomViaApi("Pantry"));

        mvc.perform(delete("/rooms/" + id))
                .andExpect(status().isNoContent());

        mvc.perform(get("/rooms/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRoom_404_whenNotFound() throws Exception {
        mvc.perform(delete("/rooms/9999"))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String createRoomViaApi(String name) throws Exception {
        return mvc.perform(post("/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private long extractId(String json) {
        String idStr = json.replaceAll(".*\"id\":(\\d+).*", "$1");
        return Long.parseLong(idStr);
    }
}