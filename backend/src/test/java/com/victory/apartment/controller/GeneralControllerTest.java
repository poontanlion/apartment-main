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

    private AppNotification mockNotif;

    @BeforeEach
    void setUp() {
        mockNotif = new AppNotification();
        mockNotif.setId("notif-123");
        mockNotif.setTitle("Test Notification");
        mockNotif.setIsRead(false);
    }

    /**
     * SUCCESS Case: Retrieve activity logs.
     * Endpoint: GET /api/activity-logs
     * Expect HTTP 200 OK and a list of activity logs.
     */
    @Test
    void getActivityLogs_Success() throws Exception {
        ActivityLog log = new ActivityLog();
        log.setAction("Test Action");
        Mockito.when(activityLogService.getAll()).thenReturn(Arrays.asList(log));

        mockMvc.perform(get("/api/activity-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("Test Action"));
    }

    /**
     * SUCCESS Case: Retrieve all notifications.
     * Endpoint: GET /api/notifications
     * Expect HTTP 200 OK and a list of notifications.
     */
    @Test
    void getNotifications_Success() throws Exception {
        Mockito.when(notifRepo.findAllByOrderByCreatedAtDesc()).thenReturn(Arrays.asList(mockNotif));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("notif-123"));
    }

    /**
     * SUCCESS Case: Create a new notification.
     * Endpoint: POST /api/notifications
     * Expect HTTP 200 OK and the created notification data.
     */
    @Test
    void createNotification_Success() throws Exception {
        Mockito.when(notifRepo.save(any(AppNotification.class))).thenReturn(mockNotif);

        AppNotification newNotif = new AppNotification();
        newNotif.setTitle("Test Notification");

        mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newNotif)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test Notification"));
    }

    /**
     * SUCCESS Case: Mark all notifications as read.
     * Endpoint: PUT /api/notifications/mark-read
     * Expect HTTP 200 OK.
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
     * Endpoint: PUT /api/notifications/{id}/read
     * Expect HTTP 200 OK and updated notification data.
     */
    @Test
    void markOneRead_Success() throws Exception {
        Mockito.when(notifRepo.findById("notif-123")).thenReturn(Optional.of(mockNotif));
        Mockito.when(notifRepo.save(any(AppNotification.class))).thenReturn(mockNotif);

        mockMvc.perform(put("/api/notifications/notif-123/read"))
                .andExpect(status().isOk());
    }

    /**
     * FAILURE Case: Mark a single notification as read when it does not exist.
     * Endpoint: PUT /api/notifications/{id}/read
     * Expect HTTP 404 Not Found.
     */
    @Test
    void markOneRead_NotFound() throws Exception {
        Mockito.when(notifRepo.findById("notif-999")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/notifications/notif-999/read"))
                .andExpect(status().isNotFound());
    }

    /**
     * SUCCESS Case: Delete a notification.
     * Endpoint: DELETE /api/notifications/{id}
     * Expect HTTP 200 OK.
     */
    @Test
    void deleteNotification_Success() throws Exception {
        Mockito.when(notifRepo.existsById("notif-123")).thenReturn(true);
        Mockito.doNothing().when(notifRepo).deleteById("notif-123");

        mockMvc.perform(delete("/api/notifications/notif-123"))
                .andExpect(status().isOk());
    }

    /**
     * FAILURE Case: Delete a notification that does not exist.
     * Endpoint: DELETE /api/notifications/{id}
     * Expect HTTP 404 Not Found.
     */
    @Test
    void deleteNotification_NotFound() throws Exception {
        Mockito.when(notifRepo.existsById("notif-999")).thenReturn(false);

        mockMvc.perform(delete("/api/notifications/notif-999"))
                .andExpect(status().isNotFound());
    }
}
