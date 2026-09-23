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
        mockUser = new AppUser("usr-12345678", "user@test.com", "password", "Test User", "123456789", "customer");
    }

    /**
     * SUCCESS Case: Login as admin.
     * Endpoint: POST /api/auth/login
     * Expect HTTP 200 OK and admin user data.
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
     * SUCCESS Case: Login as a regular database user.
     * Endpoint: POST /api/auth/login
     * Expect HTTP 200 OK and user data.
     */
    @Test
    void login_DbUserSuccess() throws Exception {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("email", "user@test.com");
        credentials.put("password", "password");

        Mockito.when(userRepo.findByEmail("user@test.com")).thenReturn(Optional.of(mockUser));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(credentials)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.email").value("user@test.com"));
    }

    /**
     * FAILURE Case: Login with invalid credentials.
     * Endpoint: POST /api/auth/login
     * Expect HTTP 401 Unauthorized.
     */
    @Test
    void login_Failure() throws Exception {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("email", "wrong@test.com");
        credentials.put("password", "wrong");

        Mockito.when(userRepo.findByEmail("wrong@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(credentials)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * SUCCESS Case: Register a new user.
     * Endpoint: POST /api/auth/register
     * Expect HTTP 200 OK and the created user data.
     */
    @Test
    void register_Success() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("fullname", "New User");
        data.put("email", "new@test.com");
        data.put("password", "pass");

        Mockito.when(userRepo.existsByEmail("new@test.com")).thenReturn(false);
        Mockito.when(userRepo.save(any(AppUser.class))).thenReturn(new AppUser("usr-1", "new@test.com", "pass", "New User", "", "customer"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(data)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.email").value("new@test.com"));
    }

    /**
     * FAILURE Case: Register with existing email.
     * Endpoint: POST /api/auth/register
     * Expect HTTP 400 Bad Request.
     */
    @Test
    void register_EmailExists() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("fullname", "Existing User");
        data.put("email", "existing@test.com");
        data.put("password", "pass");

        Mockito.when(userRepo.existsByEmail("existing@test.com")).thenReturn(true);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(data)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * FAILURE Case: Register with missing required fields.
     * Endpoint: POST /api/auth/register
     * Expect HTTP 400 Bad Request.
     */
    @Test
    void register_MissingFields() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("fullname", "User");
        // missing email and password

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(data)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * SUCCESS Case: Update existing user profile.
     * Endpoint: PUT /api/auth/profile
     * Expect HTTP 200 OK and updated user data.
     */
    @Test
    void updateProfile_Success() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("email", "user@test.com");
        data.put("fullname", "Updated Name");
        data.put("phone", "987654321");

        Mockito.when(userRepo.findByEmail("user@test.com")).thenReturn(Optional.of(mockUser));
        Mockito.when(userRepo.save(any(AppUser.class))).thenAnswer(i -> i.getArguments()[0]);

        mockMvc.perform(put("/api/auth/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(data)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.fullname").value("Updated Name"));
    }

    /**
     * SUCCESS Case: Update profile falling back to admin.
     * Endpoint: PUT /api/auth/profile
     * Expect HTTP 200 OK and admin role data.
     */
    @Test
    void updateProfile_AdminFallback() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("email", "admin@test.com");
        data.put("fullname", "Admin Test");

        Mockito.when(userRepo.findByEmail("admin@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/auth/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(data)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.role").value("admin"));
    }

    /**
     * FAILURE Case: Update profile with missing required fields.
     * Endpoint: PUT /api/auth/profile
     * Expect HTTP 400 Bad Request.
     */
    @Test
    void updateProfile_MissingFields() throws Exception {
        Map<String, String> data = new HashMap<>();
        // missing email and fullname

        mockMvc.perform(put("/api/auth/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(data)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
