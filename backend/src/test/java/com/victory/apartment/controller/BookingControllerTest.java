package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.model.Booking;
import com.victory.apartment.model.Room;
import com.victory.apartment.repository.AppNotificationRepository;
import com.victory.apartment.repository.BookingRepository;
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
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test cases for BookingController.
 * Covers success and failure scenarios for the API endpoints.
 */
@WebMvcTest(BookingController.class)
public class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private RoomRepository roomRepository;

    @MockBean
    private AppNotificationRepository notifRepo;

    @MockBean
    private ActivityLogService logService;

    @Autowired
    private ObjectMapper objectMapper;

    private Booking mockBooking;
    private Room mockRoom;

    @BeforeEach
    void setUp() {
        mockBooking = new Booking();
        mockBooking.setId("bk-123");
        mockBooking.setRoomId("rm-123");
        mockBooking.setGuestName("John Doe");
        mockBooking.setStatus("Pending");
        mockBooking.setCheckIn("2026-09-20");
        mockBooking.setCheckOut("2027-09-20");

        mockRoom = new Room();
        mockRoom.setId("rm-123");
        mockRoom.setStatus("Available");
    }

    /**
     * SUCCESS Case: Retrieve all bookings.
     */
    @Test
    void getAllBookings_Success() throws Exception {
        Mockito.when(bookingRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(mockBooking));

        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("bk-123"));
    }

    /**
     * SUCCESS Case: Create booking when room is available.
     */
    @Test
    void createBooking_Success() throws Exception {
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));
        Mockito.when(bookingRepository.findByRoomIdAndStatusIn(any(), any())).thenReturn(Collections.emptyList());
        Mockito.when(bookingRepository.save(any(Booking.class))).thenReturn(mockBooking);

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockBooking)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("bk-123"));
    }

    /**
     * FAILURE Case: Create booking when room is Occupied.
     * Expect HTTP 409 Conflict.
     */
    @Test
    void createBooking_RoomOccupied_Conflict() throws Exception {
        mockRoom.setStatus("Occupied");
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockBooking)))
                .andExpect(status().isConflict());
    }

    /**
     * SUCCESS Case: Cancel an existing booking.
     */
    @Test
    void cancelBooking_Success() throws Exception {
        Mockito.when(bookingRepository.findById("bk-123")).thenReturn(Optional.of(mockBooking));
        Mockito.when(bookingRepository.save(any(Booking.class))).thenReturn(mockBooking);

        mockMvc.perform(put("/api/bookings/bk-123/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Cancelled"));
    }

    /**
     * FAILURE Case: Cancel a non-existent booking.
     */
    @Test
    void cancelBooking_NotFound() throws Exception {
        Mockito.when(bookingRepository.findById("bk-999")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/bookings/bk-999/cancel"))
                .andExpect(status().isNotFound());
    }
}
