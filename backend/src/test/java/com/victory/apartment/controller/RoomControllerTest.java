package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.model.Room;
import com.victory.apartment.model.RoomPrice;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.service.ActivityLogService;
import com.victory.apartment.service.PriceRuleException;
import com.victory.apartment.service.RoomPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
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

    @MockBean
    private RoomPriceService roomPriceService;

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

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
        Mockito.when(roomRepository.save(any(Room.class))).thenAnswer(call -> call.getArgument(0));

        Room incoming = new Room();
        incoming.setRoomName("Renamed");

        mockMvc.perform(put("/api/rooms/rm-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incoming)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomName").value("Renamed"))
                .andExpect(jsonPath("$.price").value(5000.0));
    }

    @Test
    void updateRoom_changedPriceGoesThroughService() throws Exception {
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));
        Mockito.when(roomRepository.save(any(Room.class))).thenAnswer(call -> call.getArgument(0));
        Mockito.when(roomPriceService.currentMonth()).thenReturn(NOVEMBER);

        Room incoming = new Room();
        incoming.setPrice(6000.0);

        mockMvc.perform(put("/api/rooms/rm-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incoming)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(6000.0));

        Mockito.verify(roomPriceService).setPrice("rm-123", 6000.0, NOVEMBER);
    }

    @Test
    void updateRoom_samePriceAddsNoHistory() throws Exception {
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));
        Mockito.when(roomRepository.save(any(Room.class))).thenAnswer(call -> call.getArgument(0));

        Room incoming = new Room();
        incoming.setPrice(5000.0);
        incoming.setStatus("Maintenance");

        mockMvc.perform(put("/api/rooms/rm-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incoming)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Maintenance"));

        Mockito.verify(roomPriceService, Mockito.never()).setPrice(anyString(), any(), any());
    }

    @Test
    void updateRoom_invalidPriceSavesNothing() throws Exception {
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));
        Mockito.when(roomPriceService.currentMonth()).thenReturn(NOVEMBER);
        Mockito.when(roomPriceService.setPrice("rm-123", -1.0, NOVEMBER))
                .thenThrow(new PriceRuleException(HttpStatus.BAD_REQUEST, "INVALID_PRICE", "Bad price"));

        Room incoming = new Room();
        incoming.setPrice(-1.0);
        incoming.setStatus("Maintenance");

        mockMvc.perform(put("/api/rooms/rm-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(incoming)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PRICE"));

        Mockito.verify(roomRepository, Mockito.never()).save(any());
    }

    @Test
    void createRoom_existingIdIs409() throws Exception {
        Mockito.when(roomRepository.existsById("rm-123")).thenReturn(true);

        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockRoom)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_ALREADY_EXISTS"));

        Mockito.verify(roomRepository, Mockito.never()).save(any());
    }

    @Test
    void getPrices_returnsCurrentMonthAndHistory() throws Exception {
        RoomPrice row = new RoomPrice();
        row.setId("rp-1");
        row.setRoomId("rm-123");
        row.setPrice(6500.0);
        row.setEffectiveMonth(LocalDate.of(2026, 12, 1));
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));
        Mockito.when(roomPriceService.currentMonth()).thenReturn(NOVEMBER);
        Mockito.when(roomPriceService.history(mockRoom)).thenReturn(List.of(row));

        mockMvc.perform(get("/api/rooms/rm-123/prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentMonth").value("2026-11"))
                .andExpect(jsonPath("$.prices[0].price").value(6500.0))
                .andExpect(jsonPath("$.prices[0].effectiveMonth").value("2026-12-01"));
    }

    @Test
    void getPrices_unknownRoomIs404() throws Exception {
        Mockito.when(roomRepository.findById("rm-999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/rooms/rm-999/prices"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void postPrice_passesParsedMonth() throws Exception {
        mockMvc.perform(post("/api/rooms/rm-123/prices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":6500,\"effectiveMonth\":\"2026-12\"}"))
                .andExpect(status().isOk());

        Mockito.verify(roomPriceService).setPrice("rm-123", 6500.0, YearMonth.of(2026, 12));
    }

    @Test
    void postPrice_malformedMonthIs400() throws Exception {
        for (String body : List.of("{\"price\":6500,\"effectiveMonth\":\"2026-13\"}", "{\"price\":6500}")) {
            mockMvc.perform(post("/api/rooms/rm-123/prices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_MONTH"));
        }
        Mockito.verify(roomPriceService, Mockito.never()).setPrice(anyString(), any(), any());
    }

    @Test
    void postPrice_ruleViolationKeepsCodeAndMessage() throws Exception {
        Mockito.when(roomPriceService.setPrice("rm-123", 6500.0, YearMonth.of(2026, 10)))
                .thenThrow(new PriceRuleException(HttpStatus.BAD_REQUEST, "PRICE_MONTH_IN_PAST", "Too late"));

        mockMvc.perform(post("/api/rooms/rm-123/prices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":6500,\"effectiveMonth\":\"2026-10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICE_MONTH_IN_PAST"))
                .andExpect(jsonPath("$.message").value("Too late"));
    }

    @Test
    void deletePrice_callsService() throws Exception {
        mockMvc.perform(delete("/api/rooms/rm-123/prices/rp-1"))
                .andExpect(status().isOk());

        Mockito.verify(roomPriceService).deleteScheduled("rm-123", "rp-1");
    }

    @Test
    void getPriceForMonth_success() throws Exception {
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));
        Mockito.when(roomPriceService.priceFor(mockRoom, YearMonth.of(2026, 12))).thenReturn(6500.0);

        mockMvc.perform(get("/api/rooms/rm-123/price").param("month", "2026-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-12"))
                .andExpect(jsonPath("$.price").value(6500.0));
    }

    @Test
    void getPriceForMonth_malformedMonthIs400() throws Exception {
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));

        mockMvc.perform(get("/api/rooms/rm-123/price").param("month", "December"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MONTH"));
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
