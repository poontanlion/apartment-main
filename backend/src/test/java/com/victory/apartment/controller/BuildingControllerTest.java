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
    private BuildingRepository buildingRepository;

    @MockBean
    private ActivityLogService logService;

    @Autowired
    private ObjectMapper objectMapper;

    private Building mockBuilding;

    @BeforeEach
    void setUp() {
        mockBuilding = new Building();
        mockBuilding.setId("bld-1");
        mockBuilding.setName("Main Building");
        mockBuilding.setCode("B1");
        mockBuilding.setFloors(5);
        mockBuilding.setTotalRooms(20);
    }

    /**
     * SUCCESS Case: Retrieve all buildings.
     * Expect HTTP 200 OK and a list of buildings.
     * Endpoint: GET /api/buildings
     */
    @Test
    void getAllBuildings_Success() throws Exception {
        Mockito.when(buildingRepository.findAllByOrderByCodeAsc()).thenReturn(Arrays.asList(mockBuilding));

        mockMvc.perform(get("/api/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("bld-1"))
                .andExpect(jsonPath("$[0].name").value("Main Building"));
    }

    /**
     * SUCCESS Case: Retrieve a specific building by ID.
     * Expect HTTP 200 OK and building data.
     * Endpoint: GET /api/buildings/{id}
     */
    @Test
    void getBuildingById_Success() throws Exception {
        Mockito.when(buildingRepository.findById("bld-1")).thenReturn(Optional.of(mockBuilding));

        mockMvc.perform(get("/api/buildings/bld-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("bld-1"))
                .andExpect(jsonPath("$.name").value("Main Building"));
    }

    /**
     * FAILURE Case: Retrieve a building by ID that does not exist.
     * Expect HTTP 404 Not Found.
     * Endpoint: GET /api/buildings/{id}
     */
    @Test
    void getBuildingById_NotFound() throws Exception {
        Mockito.when(buildingRepository.findById("bld-999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/buildings/bld-999"))
                .andExpect(status().isNotFound());
    }

    /**
     * SUCCESS Case: Create a new building.
     * Expect HTTP 200 OK and created building data.
     * Endpoint: POST /api/buildings
     */
    @Test
    void createBuilding_Success() throws Exception {
        Mockito.when(buildingRepository.save(any(Building.class))).thenReturn(mockBuilding);

        Building newBuilding = new Building();
        newBuilding.setName("Main Building");
        newBuilding.setCode("B1");

        mockMvc.perform(post("/api/buildings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newBuilding)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Main Building"));
    }

    /**
     * SUCCESS Case: Update an existing building.
     * Expect HTTP 200 OK and updated building data.
     * Endpoint: PUT /api/buildings/{id}
     */
    @Test
    void updateBuilding_Success() throws Exception {
        Mockito.when(buildingRepository.findById("bld-1")).thenReturn(Optional.of(mockBuilding));
        
        Building updatedBuilding = new Building();
        updatedBuilding.setId("bld-1");
        updatedBuilding.setName("Updated Building");
        
        Mockito.when(buildingRepository.save(any(Building.class))).thenReturn(updatedBuilding);

        mockBuilding.setName("Updated Building");

        mockMvc.perform(put("/api/buildings/bld-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockBuilding)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Building"));
    }

    /**
     * FAILURE Case: Update a building that does not exist.
     * Expect HTTP 404 Not Found.
     * Endpoint: PUT /api/buildings/{id}
     */
    @Test
    void updateBuilding_NotFound() throws Exception {
        Mockito.when(buildingRepository.findById("bld-999")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/buildings/bld-999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockBuilding)))
                .andExpect(status().isNotFound());
    }

    /**
     * SUCCESS Case: Delete an existing building.
     * Expect HTTP 200 OK.
     * Endpoint: DELETE /api/buildings/{id}
     */
    @Test
    void deleteBuilding_Success() throws Exception {
        Mockito.when(buildingRepository.findById("bld-1")).thenReturn(Optional.of(mockBuilding));
        Mockito.doNothing().when(buildingRepository).deleteById("bld-1");

        mockMvc.perform(delete("/api/buildings/bld-1"))
                .andExpect(status().isOk());
    }

    /**
     * FAILURE Case: Delete a building that does not exist.
     * Expect HTTP 404 Not Found.
     * Endpoint: DELETE /api/buildings/{id}
     */
    @Test
    void deleteBuilding_NotFound() throws Exception {
        Mockito.when(buildingRepository.findById("bld-999")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/buildings/bld-999"))
                .andExpect(status().isNotFound());
    }
}
