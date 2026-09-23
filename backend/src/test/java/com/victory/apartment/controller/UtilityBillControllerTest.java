package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.model.Room;
import com.victory.apartment.model.UtilityBill;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.repository.UtilityBillRepository;
import com.victory.apartment.service.ActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test cases for UtilityBillController.
 * Covers success and failure scenarios for the API endpoints.
 */
@WebMvcTest(UtilityBillController.class)
public class UtilityBillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UtilityBillRepository utilityBillRepository;

    @MockBean
    private RoomRepository roomRepository;

    @MockBean
    private ActivityLogService logService;

    @Autowired
    private ObjectMapper objectMapper;

    private UtilityBill mockBill;
    private Room mockRoom;

    @BeforeEach
    void setUp() {
        mockBill = new UtilityBill();
        mockBill.setId("bill-123");
        mockBill.setInvoiceNo("INV-001");
        mockBill.setRoomId("rm-123");
        mockBill.setRoomNumber("A101");
        mockBill.setStatus("Pending");
        mockBill.setTotalAmount(6000.0);
        mockBill.setCreatedAt(LocalDateTime.now());

        mockRoom = new Room();
        mockRoom.setId("rm-123");
        mockRoom.setBuildingId("bld-1");
        mockRoom.setRoomNumber("A101");
    }

    /**
     * SUCCESS Case: Retrieve all utility bills without buildingId filter.
     * Expect HTTP 200 OK and a list of bills.
     */
    @Test
    void getAllBills_Success() throws Exception {
        Mockito.when(utilityBillRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(mockBill));

        mockMvc.perform(get("/api/utility-bills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("bill-123"))
                .andExpect(jsonPath("$[0].invoiceNo").value("INV-001"));
    }

    /**
     * SUCCESS Case: Retrieve utility bills with buildingId filter.
     * Expect HTTP 200 OK and a filtered list of bills.
     */
    @Test
    void getAllBillsWithFilter_Success() throws Exception {
        Mockito.when(utilityBillRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(mockBill));
        Mockito.when(roomRepository.findByBuildingIdOrderByRoomNumberAsc("bld-1")).thenReturn(Arrays.asList(mockRoom));

        mockMvc.perform(get("/api/utility-bills").param("buildingId", "bld-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("bill-123"));
    }

    /**
     * SUCCESS Case: Create a new utility bill.
     * Expect HTTP 200 OK and the created bill data.
     */
    @Test
    void createBill_Success() throws Exception {
        Mockito.when(utilityBillRepository.save(any(UtilityBill.class))).thenReturn(mockBill);
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));

        UtilityBill newBill = new UtilityBill();
        newBill.setRoomId("rm-123");
        newBill.setCurrWaterMeter(110.0);
        newBill.setPrevWaterMeter(100.0);
        newBill.setCurrElectricMeter(220.0);
        newBill.setPrevElectricMeter(200.0);

        mockMvc.perform(post("/api/utility-bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newBill)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("bill-123"));
    }

    /**
     * SUCCESS Case: Update an existing utility bill.
     * Expect HTTP 200 OK.
     */
    @Test
    void updateBill_Success() throws Exception {
        Mockito.when(utilityBillRepository.existsById("bill-123")).thenReturn(true);
        Mockito.when(utilityBillRepository.save(any(UtilityBill.class))).thenReturn(mockBill);

        mockMvc.perform(put("/api/utility-bills/bill-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockBill)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("bill-123"));
    }

    /**
     * FAILURE Case: Update a utility bill that does not exist.
     * Expect HTTP 404 Not Found.
     */
    @Test
    void updateBill_NotFound() throws Exception {
        Mockito.when(utilityBillRepository.existsById("bill-999")).thenReturn(false);

        mockMvc.perform(put("/api/utility-bills/bill-999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockBill)))
                .andExpect(status().isNotFound());
    }

    /**
     * SUCCESS Case: Update status of a utility bill.
     * Expect HTTP 200 OK.
     */
    @Test
    void updateStatus_Success() throws Exception {
        Mockito.when(utilityBillRepository.findById("bill-123")).thenReturn(Optional.of(mockBill));
        Mockito.when(utilityBillRepository.save(any(UtilityBill.class))).thenReturn(mockBill);

        Map<String, String> req = new HashMap<>();
        req.put("status", "Paid");

        mockMvc.perform(put("/api/utility-bills/bill-123/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Paid")); // mockBill originally has "Pending", but the controller modifies the entity passed to save
    }

    /**
     * FAILURE Case: Update status of a utility bill that does not exist.
     * Expect HTTP 404 Not Found.
     */
    @Test
    void updateStatus_NotFound() throws Exception {
        Mockito.when(utilityBillRepository.findById("bill-999")).thenReturn(Optional.empty());

        Map<String, String> req = new HashMap<>();
        req.put("status", "Paid");

        mockMvc.perform(put("/api/utility-bills/bill-999/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }
}
