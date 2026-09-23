package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.model.Lease;
import com.victory.apartment.service.LeaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test cases for LeaseController.
 * Covers success and failure scenarios for the API endpoints.
 */
@WebMvcTest(LeaseController.class)
public class LeaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaseService leaseService;

    @Autowired
    private ObjectMapper objectMapper;

    private Lease mockLease;

    @BeforeEach
    void setUp() {
        mockLease = new Lease();
        mockLease.setId("lease-123");
        mockLease.setRoomId("rm-123");
        mockLease.setTenantId("tenant-1");
    }

    /**
     * SUCCESS Case: Retrieve all leases.
     * Expect HTTP 200 OK.
     */
    @Test
    void getAllLeases_Success() throws Exception {
        Mockito.when(leaseService.getAll()).thenReturn(Arrays.asList(mockLease));

        mockMvc.perform(get("/api/leases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("lease-123"));
    }

    /**
     * SUCCESS Case: Check lease conflict.
     * Expect HTTP 200 OK.
     */
    @Test
    void checkConflict_Success() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("hasConflict", false);
        Mockito.when(leaseService.checkConflict(anyString(), anyString(), anyString(), any())).thenReturn(result);

        Map<String, String> req = new HashMap<>();
        req.put("roomId", "rm-123");
        req.put("startDate", "2024-01-01");
        req.put("endDate", "2024-12-31");
        req.put("excludeLeaseId", null);

        mockMvc.perform(post("/api/leases/check-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasConflict").value(false));
    }

    /**
     * SUCCESS Case: Create a new lease successfully.
     * Expect HTTP 200 OK.
     */
    @Test
    void createLease_Success() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("lease", mockLease);
        Mockito.when(leaseService.saveLease(any(Lease.class))).thenReturn(result);

        mockMvc.perform(post("/api/leases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockLease)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * FAILURE Case: Create a new lease with conflict.
     * Expect HTTP 400 Bad Request.
     */
    @Test
    void createLease_Conflict() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "Room is already leased");
        Mockito.when(leaseService.saveLease(any(Lease.class))).thenReturn(result);

        mockMvc.perform(post("/api/leases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockLease)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * SUCCESS Case: Update an existing lease.
     * Expect HTTP 200 OK.
     */
    @Test
    void updateLease_Success() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("lease", mockLease);
        Mockito.when(leaseService.saveLease(any(Lease.class))).thenReturn(result);

        mockMvc.perform(put("/api/leases/lease-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockLease)))
                .andExpect(status().isOk());
    }

    /**
     * SUCCESS Case: Terminate a lease.
     * Expect HTTP 200 OK.
     */
    @Test
    void terminateLease_Success() throws Exception {
        Mockito.doNothing().when(leaseService).terminate("lease-123");

        mockMvc.perform(put("/api/leases/lease-123/terminate"))
                .andExpect(status().isOk());
    }
}
