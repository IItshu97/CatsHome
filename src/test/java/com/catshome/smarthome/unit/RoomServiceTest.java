package com.catshome.smarthome.unit;

import com.catshome.smarthome.dto.RoomRequest;
import com.catshome.smarthome.dto.RoomResponse;
import com.catshome.smarthome.entity.Room;
import com.catshome.smarthome.exception.DuplicateResourceException;
import com.catshome.smarthome.exception.ResourceNotFoundException;
import com.catshome.smarthome.repository.RoomRepository;
import com.catshome.smarthome.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock RoomRepository repo;
    @InjectMocks RoomService service;

    private Room livingRoom;

    @BeforeEach
    void setUp() {
        livingRoom = room(1L, "Living Room");
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_returnsMappedResponses() {
        when(repo.findAll()).thenReturn(List.of(livingRoom, room(2L, "Bedroom")));

        List<RoomResponse> result = service.findAll();

        assertEquals(2, result.size());
        assertEquals("Living Room", result.get(0).name());
        assertEquals("Bedroom", result.get(1).name());
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returnsRoom() {
        when(repo.findById(1L)).thenReturn(Optional.of(livingRoom));

        RoomResponse result = service.findById(1L);

        assertEquals(1L, result.id());
        assertEquals("Living Room", result.name());
    }

    @Test
    void findById_throwsWhenNotFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(99L));
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_persistsAndReturnsNewRoom() {
        when(repo.existsByName("Kitchen")).thenReturn(false);
        when(repo.save(any())).thenAnswer(inv -> {
            Room r = inv.getArgument(0);
            r.setId(3L);
            return r;
        });

        RoomResponse result = service.create(new RoomRequest("Kitchen"));

        assertEquals("Kitchen", result.name());
        verify(repo).save(any(Room.class));
    }

    @Test
    void create_throwsOnDuplicateName() {
        when(repo.existsByName("Living Room")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> service.create(new RoomRequest("Living Room")));

        verify(repo, never()).save(any());
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_renamesRoom() {
        when(repo.findById(1L)).thenReturn(Optional.of(livingRoom));
        when(repo.findByName("Salon")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse result = service.update(1L, new RoomRequest("Salon"));

        assertEquals("Salon", result.name());
    }

    @Test
    void update_allowsKeepingOwnName() {
        when(repo.findById(1L)).thenReturn(Optional.of(livingRoom));
        // findByName returns self — should NOT throw
        when(repo.findByName("Living Room")).thenReturn(Optional.of(livingRoom));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.update(1L, new RoomRequest("Living Room")));
    }

    @Test
    void update_throwsWhenNameTakenByOtherRoom() {
        Room bedroom = room(2L, "Bedroom");
        when(repo.findById(1L)).thenReturn(Optional.of(livingRoom));
        when(repo.findByName("Bedroom")).thenReturn(Optional.of(bedroom));

        assertThrows(DuplicateResourceException.class,
                () -> service.update(1L, new RoomRequest("Bedroom")));
    }

    @Test
    void update_throwsWhenRoomNotFound() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.update(99L, new RoomRequest("Anything")));
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_callsRepositoryDelete() {
        when(repo.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(repo).deleteById(1L);
    }

    @Test
    void delete_throwsWhenNotFound() {
        when(repo.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.delete(99L));
        verify(repo, never()).deleteById(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Room room(Long id, String name) {
        Room r = new Room();
        r.setId(id);
        r.setName(name);
        return r;
    }
}