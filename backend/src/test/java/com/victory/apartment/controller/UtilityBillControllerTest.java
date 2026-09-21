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
import java.util.Collections;
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
        mockBill.setRoomId("rm-123");
        mockBill.setRoomNumber("A101");
        mockBill.setInvoiceNo("INV-2609-01");
        mockBill.setStatus("Pending");
        mockBill.setPrevWaterMeter(100.0);
        mockBill.setCurrWaterMeter(110.0);
        mockBill.setWaterRate(9.0);
        mockBill.setPrevElectricMeter(200.0);
        mockBill.setCurrElectricMeter(250.0);
        mockBill.setElectricRate(4.0);
        mockBill.setRentAmount(5500.0);
        mockBill.setCommonFee(300.0);
        mockBill.setCreatedAt(LocalDateTime.now());

        mockRoom = new Room();
        mockRoom.setId("rm-123");
        mockRoom.setRoomNumber("A101");
        mockRoom.setBuildingId("bld-1");
    }

    /**
     * SUCCESS Case: Retrieve all utility bills with no buildingId filter.
     * Expect HTTP 200 OK and a list of bills.
     */
    @Test
    void getAllBills_Success() throws Exception {
        Mockito.when(utilityBillRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(mockBill));

        mockMvc.perform(get("/api/utility-bills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("bill-123"))
                .andExpect(jsonPath("$[0].roomNumber").value("A101"));
    }

    /**
     * SUCCESS Case: Retrieve utility bills filtered by building ID.
     * Expect HTTP 200 OK and a filtered list of bills.
     */
    @Test
    void getAllBills_FilterByBuilding() throws Exception {
        Mockito.when(utilityBillRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(mockBill));
        Mockito.when(roomRepository.findByBuildingIdOrderByRoomNumberAsc("bld-1")).thenReturn(Arrays.asList(mockRoom));

        mockMvc.perform(get("/api/utility-bills").param("buildingId", "bld-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("bill-123"))
                .andExpect(jsonPath("$[0].roomNumber").value("A101"));
    }

    /**
     * SUCCESS Case: Create a new utility bill and verify auto-calculation of amounts.
     * Expect HTTP 200 OK and the created bill data.
     */
    @Test
    void createBill_Success() throws Exception {
        Mockito.when(utilityBillRepository.save(any(UtilityBill.class))).thenAnswer(i -> {
            UtilityBill b = i.getArgument(0);
            b.setId("bill-123");
            return b;
        });
        Mockito.when(roomRepository.findById("rm-123")).thenReturn(Optional.of(mockRoom));

        mockMvc.perform(post("/api/utility-bills")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockBill)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("bill-123"))
                .andExpect(jsonPath("$.waterAmount").value(90.0)) // (110-100)*9
                .andExpect(jsonPath("$.electricAmount").value(200.0)) // (250-200)*4
                .andExpect(jsonPath("$.totalAmount").value(6090.0)); // 5500 + 300 + 90 + 200
    }

    /**
     * SUCCESS Case: Update an existing utility bill.
     * Expect HTTP 200 OK.
     */
    @Test
    void updateBill_Success() throws Exception {
        Mockito.when(utilityBillRepository.existsById("bill-123")).thenReturn(true);
        Mockito.when(utilityBillRepository.save(any(UtilityBill.class))).thenReturn(mockBill);

        mockBill.setRentAmount(6000.0);

        mockMvc.perform(put("/api/utility-bills/bill-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockBill)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentAmount").value(6000.0));
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
     * SUCCESS Case: Update the status of a utility bill.
     * Expect HTTP 200 OK.
     */
    @Test
    void updateBillStatus_Success() throws Exception {
        Mockito.when(utilityBillRepository.findById("bill-123")).thenReturn(Optional.of(mockBill));
        Mockito.when(utilityBillRepository.save(any(UtilityBill.class))).thenReturn(mockBill);

        Map<String, String> requestBody = Collections.singletonMap("status", "Paid");

        mockMvc.perform(put("/api/utility-bills/bill-123/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Paid"));
    }

    /**
     * FAILURE Case: Update the status of a utility bill that does not exist.
     * Expect HTTP 404 Not Found.
     */
    @Test
    void updateBillStatus_NotFound() throws Exception {
        Mockito.when(utilityBillRepository.findById("bill-999")).thenReturn(Optional.empty());

        Map<String, String> requestBody = Collections.singletonMap("status", "Paid");

        mockMvc.perform(put("/api/utility-bills/bill-999/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isNotFound());
    }
}
