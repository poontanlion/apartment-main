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

    @BeforeEach
    void setUp() {
        mockTask = new MaintenanceTask();
        mockTask.setId("mt-123");
        mockTask.setTaskNo("MNT-001");
        mockTask.setRoomNumber("A101");
        mockTask.setStatus("Pending");
        mockTask.setReporterEmail("test@example.com");

        mockSupply = new SupplyItem();
        mockSupply.setId("sup-123");
        mockSupply.setName("Bulb");
        mockSupply.setStockQuantity(10);

        mockLog = new MaintenanceLog();
        mockLog.setId("log-123");
        mockLog.setTaskNo("MNT-001");

        mockReminder = new ScheduledReminder();
        mockReminder.setId("rem-123");
        mockReminder.setTitle("Filter Change");
        mockReminder.setIsActive(true);
    }

    /**
     * SUCCESS Case: Retrieve all tasks with no buildingId filter.
     * Expect HTTP 200 OK.
     */
    @Test
    void getAllTasks_Success() throws Exception {
        Mockito.when(taskRepo.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(mockTask));

        mockMvc.perform(get("/api/maintenance-tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("mt-123"));
    }

    /**
     * SUCCESS Case: Retrieve tasks by user email.
     * Expect HTTP 200 OK.
     */
    @Test
    void getTasksByUserEmail_Success() throws Exception {
        Mockito.when(taskRepo.findByReporterEmailIgnoreCaseOrderByCreatedAtDesc("test@example.com"))
                .thenReturn(Arrays.asList(mockTask));

        mockMvc.perform(get("/api/maintenance-tasks/user/test@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("mt-123"));
    }

    /**
     * SUCCESS Case: Create a new maintenance task.
     * Expect HTTP 200 OK.
     */
    @Test
    void createTask_Success() throws Exception {
        Mockito.when(taskRepo.save(any(MaintenanceTask.class))).thenReturn(mockTask);
        Mockito.when(notifRepo.save(any())).thenReturn(null);

        mockMvc.perform(post("/api/maintenance-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockTask)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("mt-123"));
    }

    /**
     * SUCCESS Case: Update a maintenance task.
     * Expect HTTP 200 OK.
     */
    @Test
    void updateTask_Success() throws Exception {
        Mockito.when(taskRepo.findById("mt-123")).thenReturn(Optional.of(mockTask));
        Mockito.when(taskRepo.save(any(MaintenanceTask.class))).thenReturn(mockTask);

        mockMvc.perform(put("/api/maintenance-tasks/mt-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockTask)))
                .andExpect(status().isOk());
    }

    /**
     * FAILURE Case: Update a task that doesn't exist.
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
        Mockito.doNothing().when(taskRepo).deleteById("mt-123");

        mockMvc.perform(delete("/api/maintenance-tasks/mt-123"))
                .andExpect(status().isNoContent());
    }

    /**
     * SUCCESS Case: Get all supplies.
     * Expect HTTP 200 OK.
     */
    @Test
    void getAllSupplies_Success() throws Exception {
        Mockito.when(supplyRepo.findAll()).thenReturn(Arrays.asList(mockSupply));

        mockMvc.perform(get("/api/supplies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sup-123"));
    }

    /**
     * SUCCESS Case: Create a supply item.
     * Expect HTTP 200 OK.
     */
    @Test
    void createSupply_Success() throws Exception {
        Mockito.when(supplyRepo.save(any(SupplyItem.class))).thenReturn(mockSupply);
        Mockito.when(supplyRepo.findById("sup-123")).thenReturn(Optional.of(mockSupply));

        mockMvc.perform(post("/api/supplies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockSupply)))
                .andExpect(status().isOk());
    }

    /**
     * SUCCESS Case: Get all logs.
     * Expect HTTP 200 OK.
     */
    @Test
    void getAllLogs_Success() throws Exception {
        Mockito.when(logRepo.findAll()).thenReturn(Arrays.asList(mockLog));

        mockMvc.perform(get("/api/maintenance-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("log-123"));
    }

    /**
     * SUCCESS Case: Create a reminder.
     * Expect HTTP 200 OK.
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
     * SUCCESS Case: Toggle reminder status.
     * Expect HTTP 200 OK.
     */
    @Test
    void toggleReminder_Success() throws Exception {
        Mockito.when(reminderRepo.findById("rem-123")).thenReturn(Optional.of(mockReminder));
        Mockito.when(reminderRepo.save(any(ScheduledReminder.class))).thenReturn(mockReminder);

        mockMvc.perform(put("/api/reminders/rem-123/toggle"))
                .andExpect(status().isOk());
    }
}
