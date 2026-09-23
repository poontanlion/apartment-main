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
    private TenantRepository repo;

    @MockBean
    private RoomRepository roomRepo;

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
        mockTenant.setUnitId("rm-1");
    }

    /**
     * SUCCESS Case: Retrieve all tenants with no building filter.
     * Endpoint: GET /api/tenants
     * Expect HTTP 200 OK and a list of all tenants.
     */
    @Test
    void getAll_SuccessNoFilter() throws Exception {
        Mockito.when(repo.findAll()).thenReturn(Arrays.asList(mockTenant));

        mockMvc.perform(get("/api/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t-123"))
                .andExpect(jsonPath("$[0].fullname").value("John Doe"));
    }

    /**
     * SUCCESS Case: Retrieve all tenants filtered by buildingId.
     * Endpoint: GET /api/tenants?buildingId={id}
     * Expect HTTP 200 OK and a list of filtered tenants.
     */
    @Test
    void getAll_SuccessWithFilter() throws Exception {
        Mockito.when(repo.findAll()).thenReturn(Arrays.asList(mockTenant));
        
        Room mockRoom = new Room();
        mockRoom.setId("rm-1");
        Mockito.when(roomRepo.findByBuildingIdOrderByRoomNumberAsc("bld-1")).thenReturn(Arrays.asList(mockRoom));

        mockMvc.perform(get("/api/tenants").param("buildingId", "bld-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t-123"));
    }

    /**
     * SUCCESS Case: Create a new tenant.
     * Endpoint: POST /api/tenants
     * Expect HTTP 200 OK and the created tenant data.
     */
    @Test
    void create_Success() throws Exception {
        Mockito.when(repo.save(any(Tenant.class))).thenReturn(mockTenant);

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
     * Endpoint: PUT /api/tenants/{id}
     * Expect HTTP 200 OK and the updated tenant data.
     */
    @Test
    void update_Success() throws Exception {
        Mockito.when(repo.findById("t-123")).thenReturn(Optional.of(mockTenant));
        Mockito.when(repo.save(any(Tenant.class))).thenReturn(mockTenant);

        Tenant updateData = new Tenant();
        updateData.setPhone("1234567890");

        mockMvc.perform(put("/api/tenants/t-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk());
    }

    /**
     * FAILURE Case: Update a tenant that does not exist.
     * Endpoint: PUT /api/tenants/{id}
     * Expect HTTP 404 Not Found.
     */
    @Test
    void update_NotFound() throws Exception {
        Mockito.when(repo.findById("t-999")).thenReturn(Optional.empty());

        Tenant updateData = new Tenant();
        updateData.setPhone("1234567890");

        mockMvc.perform(put("/api/tenants/t-999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isNotFound());
    }

    /**
     * SUCCESS Case: Delete a tenant.
     * Endpoint: DELETE /api/tenants/{id}
     * Expect HTTP 200 OK.
     */
    @Test
    void delete_Success() throws Exception {
        Mockito.when(repo.existsById("t-123")).thenReturn(true);
        Mockito.doNothing().when(repo).deleteById("t-123");

        mockMvc.perform(delete("/api/tenants/t-123"))
                .andExpect(status().isOk());
    }

    /**
     * FAILURE Case: Delete a tenant that does not exist.
     * Endpoint: DELETE /api/tenants/{id}
     * Expect HTTP 404 Not Found.
     */
    @Test
    void delete_NotFound() throws Exception {
        Mockito.when(repo.existsById("t-999")).thenReturn(false);

        mockMvc.perform(delete("/api/tenants/t-999"))
                .andExpect(status().isNotFound());
    }
}
