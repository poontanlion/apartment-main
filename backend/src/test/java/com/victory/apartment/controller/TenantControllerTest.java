package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.model.Room;
import com.victory.apartment.model.Tenant;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.repository.TenantRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test cases for TenantController.
 * Covers success and failure scenarios for the API endpoints.
 */
@WebMvcTest(TenantController.class)
public class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private RoomRepository roomRepository;

    @MockBean
    private ActivityLogService logService;

    @Autowired
    private ObjectMapper objectMapper;

    private Tenant mockTenant;

    @BeforeEach
    void setUp() {
        mockTenant = new Tenant();
        mockTenant.setId("t-123");
        mockTenant.setFullname("John Doe");
        mockTenant.setUnitId("rm-123");
    }

    /**
     * SUCCESS Case: Retrieve all tenants.
     * Expect HTTP 200 OK and a list of tenants.
     * Endpoint: GET /api/tenants
     */
    @Test
    void getAllTenants_Success() throws Exception {
        Mockito.when(tenantRepository.findAll()).thenReturn(Arrays.asList(mockTenant));

        mockMvc.perform(get("/api/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t-123"))
                .andExpect(jsonPath("$[0].fullname").value("John Doe"));
    }

    /**
     * SUCCESS Case: Retrieve all tenants filtered by building ID.
     * Expect HTTP 200 OK and a filtered list of tenants.
     * Endpoint: GET /api/tenants?buildingId=bld-1
     */
    @Test
    void getAllTenants_FilterByBuilding() throws Exception {
        Room room = new Room();
        room.setId("rm-123");
        room.setBuildingId("bld-1");
        
        Mockito.when(tenantRepository.findAll()).thenReturn(Arrays.asList(mockTenant));
        Mockito.when(roomRepository.findByBuildingIdOrderByRoomNumberAsc("bld-1")).thenReturn(Arrays.asList(room));

        mockMvc.perform(get("/api/tenants").param("buildingId", "bld-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t-123"));
    }

    /**
     * SUCCESS Case: Create a new tenant.
     * Expect HTTP 200 OK and created tenant data.
     * Endpoint: POST /api/tenants
     */
    @Test
    void createTenant_Success() throws Exception {
        Mockito.when(tenantRepository.save(any(Tenant.class))).thenReturn(mockTenant);

        Tenant newTenant = new Tenant();
        newTenant.setFullname("John Doe");

        mockMvc.perform(post("/api/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newTenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullname").value("John Doe"));
    }

    /**
     * SUCCESS Case: Update an existing tenant.
     * Expect HTTP 200 OK and updated tenant data.
     * Endpoint: PUT /api/tenants/{id}
     */
    @Test
    void updateTenant_Success() throws Exception {
        Mockito.when(tenantRepository.findById("t-123")).thenReturn(Optional.of(mockTenant));
        
        Tenant updatedTenant = new Tenant();
        updatedTenant.setId("t-123");
        updatedTenant.setFullname("Jane Doe");
        
        Mockito.when(tenantRepository.save(any(Tenant.class))).thenReturn(updatedTenant);

        mockTenant.setFullname("Jane Doe");

        mockMvc.perform(put("/api/tenants/t-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockTenant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullname").value("Jane Doe"));
    }

    /**
     * FAILURE Case: Update a tenant that does not exist.
     * Expect HTTP 404 Not Found.
     * Endpoint: PUT /api/tenants/{id}
     */
    @Test
    void updateTenant_NotFound() throws Exception {
        Mockito.when(tenantRepository.findById("t-999")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/tenants/t-999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockTenant)))
                .andExpect(status().isNotFound());
    }

    /**
     * SUCCESS Case: Delete an existing tenant.
     * Expect HTTP 200 OK.
     * Endpoint: DELETE /api/tenants/{id}
     */
    @Test
    void deleteTenant_Success() throws Exception {
        Mockito.when(tenantRepository.existsById("t-123")).thenReturn(true);
        Mockito.doNothing().when(tenantRepository).deleteById("t-123");

        mockMvc.perform(delete("/api/tenants/t-123"))
                .andExpect(status().isOk());
    }

    /**
     * FAILURE Case: Delete a tenant that does not exist.
     * Expect HTTP 404 Not Found.
     * Endpoint: DELETE /api/tenants/{id}
     */
    @Test
    void deleteTenant_NotFound() throws Exception {
        Mockito.when(tenantRepository.existsById("t-999")).thenReturn(false);

        mockMvc.perform(delete("/api/tenants/t-999"))
                .andExpect(status().isNotFound());
    }
}
