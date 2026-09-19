package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.model.Room;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.service.ActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test cases for RoomController.
 * Covers success and failure scenarios for the API endpoints.
 */
@WebMvcTest(RoomController.class)
public class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoomRepository roomRepository;

    @MockBean
    private ActivityLogService logService;

    @Autowired
    private ObjectMapper objectMapper;

    private Room mockRoom;

    @BeforeEach
    void setUp() {
        mockRoom = new Room();
        mockRoom.setId("rm-123");
        mockRoom.setRoomNumber("A101");
        mockRoom.setBuildingId("bld-1");
        mockRoom.setPrice(5000.0);
        mockRoom.setStatus("Available");
    }

    /**
     * SUCCESS Case: Retrieve all rooms with no buildingId filter.
     * Expect HTTP 200 OK and a list of rooms.
     */
    @Test
    void getAllRooms_Success() throws Exception {
        Mockito.when(roomRepository.findAllByOrderByRoomNumberAsc()).thenReturn(Arrays.asList(mockRoom));

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("rm-123"))
                .andExpect(jsonPath("$[0].roomNumber").value("A101"));
    }

    /**
     * SUCCESS Case: Retrieve a specific room by ID.
     * Expect HTTP 200 OK and room data.
     */
    @Test
    void getRoomById_Success() throws Exception {
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));

        mockMvc.perform(get("/api/rooms/rm-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("rm-123"));
    }

    /**
     * FAILURE Case: Retrieve a specific room by ID that does not exist.
     * Expect HTTP 404 Not Found.
     */
    @Test
    void getRoomById_NotFound() throws Exception {
        Mockito.when(roomRepository.findById("rm-999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/rooms/rm-999"))
                .andExpect(status().isNotFound());
    }

    /**
     * SUCCESS Case: Create a new room.
     * Expect HTTP 200 OK and the created room data.
     */
    @Test
    void createRoom_Success() throws Exception {
        Mockito.when(roomRepository.save(any(Room.class))).thenReturn(mockRoom);

        Room newRoom = new Room();
        newRoom.setRoomNumber("A101");

        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newRoom)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber").value("A101"));
    }

    /**
     * SUCCESS Case: Update an existing room.
     * Expect HTTP 200 OK.
     */
    @Test
    void updateRoom_Success() throws Exception {
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));
        Mockito.when(roomRepository.save(any(Room.class))).thenReturn(mockRoom);

        mockRoom.setPrice(6000.0);

        mockMvc.perform(put("/api/rooms/rm-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockRoom)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(6000.0));
    }

    /**
     * FAILURE Case: Update a room that does not exist.
     * Expect HTTP 404 Not Found.
     */
    @Test
    void updateRoom_NotFound() throws Exception {
        Mockito.when(roomRepository.findById("rm-999")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/rooms/rm-999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockRoom)))
                .andExpect(status().isNotFound());
    }

    /**
     * SUCCESS Case: Delete a room.
     * Expect HTTP 200 OK.
     */
    @Test
    void deleteRoom_Success() throws Exception {
        Mockito.when(roomRepository.existsById("rm-123")).thenReturn(true);
        Mockito.doNothing().when(roomRepository).deleteById("rm-123");

        mockMvc.perform(delete("/api/rooms/rm-123"))
                .andExpect(status().isOk());
    }

    /**
     * FAILURE Case: Delete a room that does not exist.
     * Expect HTTP 404 Not Found.
     */
    @Test
    void deleteRoom_NotFound() throws Exception {
        Mockito.when(roomRepository.existsById("rm-999")).thenReturn(false);

        mockMvc.perform(delete("/api/rooms/rm-999"))
                .andExpect(status().isNotFound());
    }
}
