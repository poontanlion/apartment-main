package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.model.Booking;
import com.victory.apartment.model.AppNotification;
import com.victory.apartment.model.Room;
import com.victory.apartment.repository.AppNotificationRepository;
import com.victory.apartment.repository.BookingRepository;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.service.ActivityLogService;
import com.victory.apartment.service.LeaseService;
import com.victory.apartment.service.RoomPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
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

    @MockBean
    private LeaseService leaseService;

    @MockBean
    private RoomPriceService roomPriceService;

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
        mockBooking.setGuestPhone("0812345678");
        mockBooking.setStatus("Pending");
        mockBooking.setCheckIn("2026-09-20");
        mockBooking.setCheckOut("2027-09-20");

        mockRoom = new Room();
        mockRoom.setId("rm-123");
        mockRoom.setStatus("Available");

        Mockito.when(leaseService.checkConflict(any(), any(), any(), any())).thenReturn(Map.of("hasConflict", false));
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

        ArgumentCaptor<AppNotification> notification = ArgumentCaptor.forClass(AppNotification.class);
        Mockito.verify(notifRepo).save(notification.capture());
        org.junit.jupiter.api.Assertions.assertFalse(notification.getValue().getMessage().contains("0812345678"));
    }

    private void bookingCanBeSaved() {
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));
        Mockito.when(bookingRepository.findByRoomIdAndStatusIn(any(), any())).thenReturn(Collections.emptyList());
        Mockito.when(bookingRepository.save(any(Booking.class))).thenAnswer(call -> call.getArgument(0));
    }

    private org.springframework.test.web.servlet.ResultActions postBooking() throws Exception {
        return mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockBooking)));
    }

    @Test
    void createBooking_pricesByCheckInMonth() throws Exception {
        bookingCanBeSaved();
        Mockito.when(roomPriceService.priceFor(mockRoom, YearMonth.of(2026, 12))).thenReturn(6500.0);
        mockBooking.setCheckIn("2026-12-20");

        postBooking().andExpect(status().isOk()).andExpect(jsonPath("$.totalPrice").value(6500.0));
    }

    @Test
    void createBooking_ignoresClientPrice() throws Exception {
        bookingCanBeSaved();
        Mockito.when(roomPriceService.priceFor(mockRoom, YearMonth.of(2026, 12))).thenReturn(6500.0);
        mockBooking.setCheckIn("2026-12-20");
        mockBooking.setTotalPrice(1.0);

        postBooking().andExpect(status().isOk()).andExpect(jsonPath("$.totalPrice").value(6500.0));
    }

    @Test
    void createBooking_withRoomNumberStillPriced() throws Exception {
        bookingCanBeSaved();
        Mockito.when(roomPriceService.priceFor(mockRoom, YearMonth.of(2026, 12))).thenReturn(6500.0);
        mockBooking.setCheckIn("2026-12-20");
        mockBooking.setRoomNumber("A101");

        postBooking().andExpect(status().isOk()).andExpect(jsonPath("$.totalPrice").value(6500.0));
    }

    @Test
    void createBooking_unparseableCheckInUsesCurrentMonth() throws Exception {
        bookingCanBeSaved();
        Mockito.when(roomPriceService.currentMonth()).thenReturn(YearMonth.of(2026, 11));
        Mockito.when(roomPriceService.priceFor(mockRoom, YearMonth.of(2026, 11))).thenReturn(6000.0);
        mockBooking.setCheckIn("bad");

        postBooking().andExpect(status().isOk()).andExpect(jsonPath("$.totalPrice").value(6000.0));
    }

    @Test
    void createBooking_withoutCheckInUsesCurrentMonth() throws Exception {
        bookingCanBeSaved();
        Mockito.when(roomPriceService.currentMonth()).thenReturn(YearMonth.of(2026, 11));
        Mockito.when(roomPriceService.priceFor(mockRoom, YearMonth.of(2026, 11))).thenReturn(6000.0);
        mockBooking.setCheckIn(null);

        postBooking().andExpect(status().isOk()).andExpect(jsonPath("$.totalPrice").value(6000.0));
    }

    @Test
    void createBooking_unknownRoomKeepsNoClientPrice() throws Exception {
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.empty());
        Mockito.when(bookingRepository.findByRoomIdAndStatusIn(any(), any())).thenReturn(Collections.emptyList());
        Mockito.when(bookingRepository.save(any(Booking.class))).thenAnswer(call -> call.getArgument(0));
        mockBooking.setTotalPrice(1.0);

        postBooking().andExpect(status().isOk()).andExpect(jsonPath("$.totalPrice").doesNotExist());
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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_OCCUPIED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createBooking_MaintenanceExplainsWhyRoomCannotBeBooked() throws Exception {
        mockRoom.setStatus("Maintenance");
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockBooking)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_MAINTENANCE"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createBooking_OverlappingApprovedBookingExplainsDateConflict() throws Exception {
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));
        Booking existing = new Booking();
        existing.setStatus("Approved");
        existing.setCheckIn("2026-09-20");
        existing.setCheckOut("2027-09-20");
        Mockito.when(bookingRepository.findByRoomIdAndStatusIn(any(), any())).thenReturn(java.util.List.of(existing));
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockBooking)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_DATE_CONFLICT"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createBooking_NonOverlappingApprovedBookingStillAllowed() throws Exception {
        mockRoom.setStatus("Reserved");
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));
        Booking existing = new Booking();
        existing.setCheckIn("2025-09-20");
        existing.setCheckOut("2026-09-20");
        Mockito.when(bookingRepository.findByRoomIdAndStatusIn(any(), any())).thenReturn(java.util.List.of(existing));
        Mockito.when(bookingRepository.save(any(Booking.class))).thenReturn(mockBooking);
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockBooking)))
                .andExpect(status().isOk());
    }

    /**
     * SUCCESS Case: Cancel an existing booking.
     */
    @Test
    void cancelBooking_Success() throws Exception {
        Mockito.when(bookingRepository.findByIdForUpdate("bk-123")).thenReturn(Optional.of(mockBooking));
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
        Mockito.when(bookingRepository.findByIdForUpdate("bk-999")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/bookings/bk-999/cancel"))
                .andExpect(status().isNotFound());
    }
}
