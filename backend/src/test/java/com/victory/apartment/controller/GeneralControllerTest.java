package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.model.ActivityLog;
import com.victory.apartment.model.AppNotification;
import com.victory.apartment.repository.AppNotificationRepository;
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
 * Test cases for GeneralController.
 * Covers success and failure scenarios for the API endpoints.
 */
@WebMvcTest(GeneralController.class)
public class GeneralControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivityLogService activityLogService;

    @MockBean
    private AppNotificationRepository notifRepo;

    @Autowired
    private ObjectMapper objectMapper;

    private ActivityLog mockLog;
    private AppNotification mockNotif;

    @BeforeEach
    void setUp() {
        mockLog = new ActivityLog();
        mockLog.setId("log-1");
        mockLog.setAction("Test Action");

        mockNotif = new AppNotification();
        mockNotif.setId("notif-1");
        mockNotif.setTitle("Test Title");
        mockNotif.setIsRead(false);
    }

    /**
     * SUCCESS Case: Retrieve all activity logs.
     * Expect HTTP 200 OK and a list of logs.
     * Endpoint: GET /api/activity-logs
     */
    @Test
    void getActivityLogs_Success() throws Exception {
        Mockito.when(activityLogService.getAll()).thenReturn(Arrays.asList(mockLog));

        mockMvc.perform(get("/api/activity-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("log-1"))
                .andExpect(jsonPath("$[0].action").value("Test Action"));
    }

    /**
     * SUCCESS Case: Retrieve all notifications.
     * Expect HTTP 200 OK and a list of notifications.
     * Endpoint: GET /api/notifications
     */
    @Test
    void getNotifications_Success() throws Exception {
        Mockito.when(notifRepo.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(mockNotif));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("notif-1"))
                .andExpect(jsonPath("$[0].title").value("Test Title"));
    }

    /**
     * SUCCESS Case: Create a new notification.
     * Expect HTTP 200 OK and created notification data.
     * Endpoint: POST /api/notifications
     */
    @Test
    void createNotification_Success() throws Exception {
        Mockito.when(notifRepo.save(any(AppNotification.class))).thenReturn(mockNotif);

        AppNotification newNotif = new AppNotification();
        newNotif.setTitle("Test Title");

        mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newNotif)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test Title"));
    }

    /**
     * SUCCESS Case: Mark all notifications as read.
     * Expect HTTP 200 OK.
     * Endpoint: PUT /api/notifications/mark-read
     */
    @Test
    void markAllRead_Success() throws Exception {
        Mockito.when(notifRepo.findAll()).thenReturn(Arrays.asList(mockNotif));
        Mockito.when(notifRepo.saveAll(any())).thenReturn(Arrays.asList(mockNotif));

        mockMvc.perform(put("/api/notifications/mark-read"))
                .andExpect(status().isOk());
    }

    /**
     * SUCCESS Case: Mark a single notification as read.
     * Expect HTTP 200 OK and updated notification.
     * Endpoint: PUT /api/notifications/{id}/read
     */
    @Test
    void markOneRead_Success() throws Exception {
        Mockito.when(notifRepo.findById("notif-1")).thenReturn(Optional.of(mockNotif));
        
        AppNotification updatedNotif = new AppNotification();
        updatedNotif.setId("notif-1");
        updatedNotif.setIsRead(true);
        
        Mockito.when(notifRepo.save(any(AppNotification.class))).thenReturn(updatedNotif);

        mockMvc.perform(put("/api/notifications/notif-1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));
    }

    /**
     * FAILURE Case: Mark a non-existent notification as read.
     * Expect HTTP 404 Not Found.
     * Endpoint: PUT /api/notifications/{id}/read
     */
    @Test
    void markOneRead_NotFound() throws Exception {
        Mockito.when(notifRepo.findById("notif-999")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/notifications/notif-999/read"))
                .andExpect(status().isNotFound());
    }

    /**
     * SUCCESS Case: Delete a notification.
     * Expect HTTP 200 OK.
     * Endpoint: DELETE /api/notifications/{id}
     */
    @Test
    void deleteNotification_Success() throws Exception {
        Mockito.when(notifRepo.existsById("notif-1")).thenReturn(true);
        Mockito.doNothing().when(notifRepo).deleteById("notif-1");

        mockMvc.perform(delete("/api/notifications/notif-1"))
                .andExpect(status().isOk());
    }

    /**
     * FAILURE Case: Delete a non-existent notification.
     * Expect HTTP 404 Not Found.
     * Endpoint: DELETE /api/notifications/{id}
     */
    @Test
    void deleteNotification_NotFound() throws Exception {
        Mockito.when(notifRepo.existsById("notif-999")).thenReturn(false);

        mockMvc.perform(delete("/api/notifications/notif-999"))
                .andExpect(status().isNotFound());
    }
}
