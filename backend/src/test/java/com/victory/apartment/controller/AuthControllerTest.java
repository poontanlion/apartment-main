package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.model.AppUser;
import com.victory.apartment.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test cases for AuthController.
 * Covers success and failure scenarios for the API endpoints.
 */
@WebMvcTest(AuthController.class)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppUserRepository userRepo;

    @Autowired
    private ObjectMapper objectMapper;

    private AppUser mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new AppUser("usr-123456", "test@example.com", "password", "Test User", "1234567890", "customer");
    }

    /**
     * SUCCESS Case: Login as admin.
     * Expect HTTP 200 OK and success=true.
     * Endpoint: POST /api/auth/login
     */
    @Test
    void login_AdminSuccess() throws Exception {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("email", "admin");
        credentials.put("password", "admin");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(credentials)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.role").value("admin"));
    }

    /**
     * FAILURE Case: Login with wrong password.
     * Expect HTTP 401 Unauthorized and success=false.
     * Endpoint: POST /api/auth/login
     */
    @Test
    void login_WrongPassword() throws Exception {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("email", "test@example.com");
        credentials.put("password", "wrongpassword");

        Mockito.when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(credentials)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * SUCCESS Case: Register a new user.
     * Expect HTTP 200 OK and success=true.
     * Endpoint: POST /api/auth/register
     */
    @Test
    void register_Success() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("fullname", "New User");
        data.put("email", "new@example.com");
        data.put("password", "password123");
        data.put("phone", "0987654321");

        Mockito.when(userRepo.existsByEmail("new@example.com")).thenReturn(false);
        Mockito.when(userRepo.save(any(AppUser.class))).thenReturn(new AppUser("usr-new", "new@example.com", "password123", "New User", "0987654321", "customer"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(data)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.email").value("new@example.com"));
    }

    /**
     * FAILURE Case: Register with a duplicate email.
     * Expect HTTP 400 Bad Request and success=false.
     * Endpoint: POST /api/auth/register
     */
    @Test
    void register_DuplicateEmail() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("fullname", "Test User");
        data.put("email", "test@example.com");
        data.put("password", "password123");

        Mockito.when(userRepo.existsByEmail("test@example.com")).thenReturn(true);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(data)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * FAILURE Case: Register with missing required fields (empty email).
     * Expect HTTP 400 Bad Request and success=false.
     * Endpoint: POST /api/auth/register
     */
    @Test
    void register_MissingFields() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("fullname", "Test User");
        data.put("email", "");
        data.put("password", "password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(data)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * SUCCESS Case: Update user profile.
     * Expect HTTP 200 OK and success=true.
     * Endpoint: PUT /api/auth/profile
     */
    @Test
    void updateProfile_Success() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("email", "test@example.com");
        data.put("fullname", "Updated Name");
        data.put("phone", "1112223333");

        Mockito.when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        
        AppUser updatedUser = new AppUser("usr-123456", "test@example.com", "password", "Updated Name", "1112223333", "customer");
        Mockito.when(userRepo.save(any(AppUser.class))).thenReturn(updatedUser);

        mockMvc.perform(put("/api/auth/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(data)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.fullname").value("Updated Name"));
    }
}
