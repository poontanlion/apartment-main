package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test cases for MaintenanceController.
 * Covers success and failure scenarios for the API endpoints.
 */
@WebMvcTest(MaintenanceController.class)
public class MaintenanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MaintenanceTaskRepository taskRepo;

    @MockBean
    private SupplyItemRepository supplyRepo;

    @MockBean
    private MaintenanceLogRepository logRepo;

    @MockBean
    private ScheduledReminderRepository reminderRepo;

    @MockBean
    private AppNotificationRepository notifRepo;

    @MockBean
    private RoomRepository roomRepo;

    @MockBean
    private ActivityLogService activityLogService;

    @Autowired
    private ObjectMapper objectMapper;

    private MaintenanceTask mockTask;
    private SupplyItem mockSupply;
    private MaintenanceLog mockLog;
    private ScheduledReminder mockReminder;
    private Room mockRoom;

    @BeforeEach
    void setUp() {
        mockTask = new MaintenanceTask();
        mockTask.setId("mt-123");
        mockTask.setRoomId("rm-123");
        mockTask.setRoomNumber("A101");
        mockTask.setStatus("Pending");
        mockTask.setCategory("Plumbing");
        mockTask.setTaskNo("MNT-202609-01");

        mockSupply = new SupplyItem();
        mockSupply.setId("sup-123");
        mockSupply.setName("Pipe");
        mockSupply.setStockQuantity(10);

        mockLog = new MaintenanceLog();
        mockLog.setId("log-123");
        mockLog.setTaskNo("MNT-202609-01");
        mockLog.setRoomNumber("A101");

        mockReminder = new ScheduledReminder();
        mockReminder.setId("rem-123");
        mockReminder.setTitle("Clean AC");
        mockReminder.setIsActive(true);

        mockRoom = new Room();
        mockRoom.setId("rm-123");
        mockRoom.setBuildingId("bld-1");
        mockRoom.setRoomNumber("A101");
        mockRoom.setStatus("Occupied");
    }

    /**
     * SUCCESS Case: Retrieve all maintenance tasks with no buildingId filter.
     * Expect HTTP 200 OK and a list of tasks.
     */
    @Test
    void getAllTasks_Success() throws Exception {
        Mockito.when(taskRepo.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(mockTask));
        Mockito.when(roomRepo.findByRoomNumber("A101")).thenReturn(Optional.of(mockRoom));

        mockMvc.perform(get("/api/maintenance-tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("mt-123"));
    }

    /**
     * SUCCESS Case: Retrieve maintenance tasks filtered by building ID.
     * Expect HTTP 200 OK and a filtered list of tasks.
     */
    @Test
    void getAllTasks_FilterByBuilding() throws Exception {
        Mockito.when(taskRepo.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(mockTask));
        Mockito.when(roomRepo.findByRoomNumber("A101")).thenReturn(Optional.of(mockRoom));
        Mockito.when(roomRepo.findByBuildingIdOrderByRoomNumberAsc("bld-1")).thenReturn(Arrays.asList(mockRoom));

        mockMvc.perform(get("/api/maintenance-tasks").param("buildingId", "bld-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("mt-123"));
    }

    /**
     * SUCCESS Case: Create a new maintenance task.
     * Expect HTTP 200 OK and the created task data.
     */
    @Test
    void createTask_Success() throws Exception {
        Mockito.when(roomRepo.findByRoomNumber("A101")).thenReturn(Optional.of(mockRoom));
        Mockito.when(taskRepo.save(any(MaintenanceTask.class))).thenReturn(mockTask);
        Mockito.when(logRepo.findByTaskNo(anyString())).thenReturn(Arrays.asList());

        mockMvc.perform(post("/api/maintenance-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockTask)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("mt-123"));
    }

    /**
     * SUCCESS Case: Update an existing maintenance task.
     * Expect HTTP 200 OK.
     */
    @Test
    void updateTask_Success() throws Exception {
        Mockito.when(taskRepo.findById("mt-123")).thenReturn(Optional.of(mockTask));
        Mockito.when(taskRepo.save(any(MaintenanceTask.class))).thenReturn(mockTask);

        mockTask.setStatus("Completed");

        mockMvc.perform(put("/api/maintenance-tasks/mt-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockTask)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Completed"));
    }

    /**
     * FAILURE Case: Update a maintenance task that does not exist.
     * Expect HTTP 404 Not Found.
     */
    @Test
    void updateTask_NotFound() throws Exception {
        Mockito.when(taskRepo.findById("mt-999")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/maintenance-tasks/mt-999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockTask)))
                .andExpect(status().isNotFound());
    }

    /**
     * SUCCESS Case: Delete a maintenance task.
     * Expect HTTP 204 No Content.
     */
    @Test
    void deleteTask_Success() throws Exception {
        Mockito.when(taskRepo.findById("mt-123")).thenReturn(Optional.of(mockTask));
        Mockito.when(logRepo.findByTaskNo(anyString())).thenReturn(Arrays.asList());
        Mockito.doNothing().when(taskRepo).deleteById("mt-123");

        mockMvc.perform(delete("/api/maintenance-tasks/mt-123"))
                .andExpect(status().isNoContent());
    }

    /**
     * FAILURE Case: Delete a maintenance task that does not exist.
     * Expect HTTP 404 Not Found.
     */
    @Test
    void deleteTask_NotFound() throws Exception {
        Mockito.when(taskRepo.findById("mt-999")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/maintenance-tasks/mt-999"))
                .andExpect(status().isNotFound());
    }

    /**
     * SUCCESS Case: Retrieve all supply items.
     * Expect HTTP 200 OK and a list of supply items.
     */
    @Test
    void getAllSupplies_Success() throws Exception {
        Mockito.when(supplyRepo.findAll()).thenReturn(Arrays.asList(mockSupply));

        mockMvc.perform(get("/api/supplies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sup-123"));
    }

    /**
     * SUCCESS Case: Create a new supply item.
     * Expect HTTP 200 OK and the created supply data.
     */
    @Test
    void createSupply_Success() throws Exception {
        Mockito.when(supplyRepo.save(any(SupplyItem.class))).thenReturn(mockSupply);
        Mockito.when(supplyRepo.findById("sup-123")).thenReturn(Optional.of(mockSupply));

        mockMvc.perform(post("/api/supplies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockSupply)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("sup-123"));
    }

    /**
     * SUCCESS Case: Retrieve all maintenance logs.
     * Expect HTTP 200 OK and a list of logs.
     */
    @Test
    void getAllLogs_Success() throws Exception {
        Mockito.when(logRepo.findAll()).thenReturn(Arrays.asList(mockLog));

        mockMvc.perform(get("/api/maintenance-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("log-123"));
    }

    /**
     * SUCCESS Case: Retrieve all scheduled reminders.
     * Expect HTTP 200 OK and a list of reminders.
     */
    @Test
    void getAllReminders_Success() throws Exception {
        Mockito.when(reminderRepo.findAll()).thenReturn(Arrays.asList(mockReminder));

        mockMvc.perform(get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("rem-123"));
    }

    /**
     * SUCCESS Case: Create a new scheduled reminder.
     * Expect HTTP 200 OK and the created reminder data.
     */
    @Test
    void createReminder_Success() throws Exception {
        Mockito.when(reminderRepo.save(any(ScheduledReminder.class))).thenReturn(mockReminder);

        mockMvc.perform(post("/api/reminders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockReminder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("rem-123"));
    }

    /**
     * SUCCESS Case: Toggle the active status of a scheduled reminder.
     * Expect HTTP 200 OK.
     */
    @Test
    void toggleReminder_Success() throws Exception {
        Mockito.when(reminderRepo.findById("rem-123")).thenReturn(Optional.of(mockReminder));
        Mockito.when(reminderRepo.save(any(ScheduledReminder.class))).thenReturn(mockReminder);

        mockMvc.perform(put("/api/reminders/rem-123/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false)); // Toggled in the controller
    }

    /**
     * FAILURE Case: Toggle the active status of a scheduled reminder that does not exist.
     * Expect HTTP 404 Not Found.
     */
    @Test
    void toggleReminder_NotFound() throws Exception {
        Mockito.when(reminderRepo.findById("rem-999")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/reminders/rem-999/toggle"))
                .andExpect(status().isNotFound());
    }
}
