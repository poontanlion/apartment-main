package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.model.Building;
import com.victory.apartment.repository.BuildingRepository;
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
 * Test cases for BuildingController.
 * Covers success and failure scenarios for the API endpoints.
 */
@WebMvcTest(BuildingController.class)
public class BuildingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BuildingRepository repo;

    @MockBean
    private ActivityLogService logService;

    @Autowired
    private ObjectMapper objectMapper;

    private Building mockBuilding;

    @BeforeEach
    void setUp() {
        mockBuilding = new Building();
        mockBuilding.setId("bld-123");
        mockBuilding.setName("Main Building");
        mockBuilding.setCode("MB");
    }

    /**
     * SUCCESS Case: Retrieve all buildings.
     * Endpoint: GET /api/buildings
     * Expect HTTP 200 OK and a list of buildings.
     */
    @Test
    void getAll_Success() throws Exception {
        Mockito.when(repo.findAllByOrderByCodeAsc()).thenReturn(Arrays.asList(mockBuilding));

        mockMvc.perform(get("/api/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("bld-123"))
                .andExpect(jsonPath("$[0].name").value("Main Building"));
    }

    /**
     * SUCCESS Case: Retrieve a specific building by ID.
     * Endpoint: GET /api/buildings/{id}
     * Expect HTTP 200 OK and building data.
     */
    @Test
    void getById_Success() throws Exception {
        Mockito.when(repo.findById("bld-123")).thenReturn(Optional.of(mockBuilding));

        mockMvc.perform(get("/api/buildings/bld-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("bld-123"));
    }

    /**
     * FAILURE Case: Retrieve a specific building by ID that does not exist.
     * Endpoint: GET /api/buildings/{id}
     * Expect HTTP 404 Not Found.
     */
    @Test
    void getById_NotFound() throws Exception {
        Mockito.when(repo.findById("bld-999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/buildings/bld-999"))
                .andExpect(status().isNotFound());
    }

    /**
     * SUCCESS Case: Create a new building.
     * Endpoint: POST /api/buildings
     * Expect HTTP 200 OK and the created building data.
     */
    @Test
    void create_Success() throws Exception {
        Mockito.when(repo.save(any(Building.class))).thenReturn(mockBuilding);

        Building newBuilding = new Building();
        newBuilding.setName("Main Building");

        mockMvc.perform(post("/api/buildings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newBuilding)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Main Building"));
    }

    /**
     * SUCCESS Case: Update an existing building.
     * Endpoint: PUT /api/buildings/{id}
     * Expect HTTP 200 OK and the updated building data.
     */
    @Test
    void update_Success() throws Exception {
        Mockito.when(repo.findById("bld-123")).thenReturn(Optional.of(mockBuilding));
        Mockito.when(repo.save(any(Building.class))).thenReturn(mockBuilding);

        Building updateData = new Building();
        updateData.setName("Updated Building");

        mockMvc.perform(put("/api/buildings/bld-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk());
    }

    /**
     * FAILURE Case: Update a building that does not exist.
     * Endpoint: PUT /api/buildings/{id}
     * Expect HTTP 404 Not Found.
     */
    @Test
    void update_NotFound() throws Exception {
        Mockito.when(repo.findById("bld-999")).thenReturn(Optional.empty());

        Building updateData = new Building();
        updateData.setName("Updated");

        mockMvc.perform(put("/api/buildings/bld-999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isNotFound());
    }

    /**
     * SUCCESS Case: Delete a building.
     * Endpoint: DELETE /api/buildings/{id}
     * Expect HTTP 200 OK.
     */
    @Test
    void delete_Success() throws Exception {
        Mockito.when(repo.findById("bld-123")).thenReturn(Optional.of(mockBuilding));
        Mockito.doNothing().when(repo).deleteById("bld-123");

        mockMvc.perform(delete("/api/buildings/bld-123"))
                .andExpect(status().isOk());
    }

    /**
     * FAILURE Case: Delete a building that does not exist.
     * Endpoint: DELETE /api/buildings/{id}
     * Expect HTTP 404 Not Found.
     */
    @Test
    void delete_NotFound() throws Exception {
        Mockito.when(repo.findById("bld-999")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/buildings/bld-999"))
                .andExpect(status().isNotFound());
    }
}
