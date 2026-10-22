package com.victory.apartment.actuator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Spring Boot Actuator Health Check endpoints.
 * Verifies that the actuator library properly exposes health indicators,
 * including database connectivity and Kubernetes liveness/readiness probes.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ActuatorHealthTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Health endpoint returns status UP and includes DB component")
    void healthEndpoint_ReturnsUpWithDetails() throws Exception {
        mockMvc.perform(get("/actuator/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    @DisplayName("Liveness probe endpoint returns status UP")
    void livenessProbe_ReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Readiness probe endpoint returns status UP")
    void readinessProbe_ReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
