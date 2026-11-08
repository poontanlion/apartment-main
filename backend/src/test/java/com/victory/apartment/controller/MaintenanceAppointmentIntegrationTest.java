package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.*;
import com.victory.apartment.TestcontainersConfiguration;
import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
import com.victory.apartment.service.maintenancepredictor.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MaintenancePredictionIntegrationTest.FixedTime.class})
class MaintenanceAppointmentIntegrationTest {
    private static final String BASE = "/api/maintenance/predictions";
    private static final String PATH = BASE + "/rm-A101/mtt-light/appointment";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RoomRepository rooms;
    @Autowired AppUserRepository users;
    @Autowired ScheduledReminderRepository reminders;
    @Autowired MaintenancePredictionRepository predictions;
    @Autowired MaintenanceTaskTypeRepository types;
    @Autowired MaintenanceLogRepository logs;
    @Autowired MaintenanceTaskRepository tasks;
    @Autowired MaintenancePredictorService predictor;
    private MockHttpSession admin;

    @BeforeEach void setup() {
        var room = new Room(); room.setId("rm-A101"); room.setRoomNumber("A101"); room.setFloor(1);
        room.setBuildingId("bld-1"); room.setBuildingName("Victory Tower A"); rooms.saveAndFlush(room);
        users.saveAndFlush(new AppUser("usr-admin-test", "appointment-admin@example.com", "test", "Admin", "", "admin"));
        admin = new MockHttpSession(); admin.setAttribute("userEmail", "appointment-admin@example.com");
    }
    @AfterEach void cleanup() {
        predictions.deleteAll(); reminders.deleteAll(); logs.deleteAll(); tasks.deleteAll(); rooms.deleteAll(); users.deleteAll();
        var type = types.findById("mtt-light").orElseThrow(); type.setDefaultIntervalDays(365); types.saveAndFlush(type);
    }
    private ResultActions putDate(String date) throws Exception {
        return mvc.perform(put(PATH).session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\"}"));
    }
    private MaintenancePrediction prediction() {
        return predictions.findByRoomIdAndTaskTypeId("rm-A101", "mtt-light").orElseThrow();
    }
    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    private void reminder(String id, String category, String date, boolean active) {
        var r = new ScheduledReminder(); r.setId(id); r.setTitle("Manual reminder"); r.setCategory(category);
        r.setRoomId("rm-A101"); r.setRoomNumber("A101"); r.setFrequency("Monthly");
        r.setNextDueDate(date); r.setIsActive(active); reminders.saveAndFlush(r);
    }
    private void task(String status) throws Exception {
        mvc.perform(post("/api/maintenance-tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"mt-close\",\"taskNo\":\"MNT-CLOSE\",\"roomId\":\"rm-A101\",\"roomNumber\":\"A101\",\"category\":\"Light bulb replacement\",\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }
    private void changeTask(String status) throws Exception {
        mvc.perform(put("/api/maintenance-tasks/mt-close").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}")).andExpect(status().isOk());
    }

    @Test void putCreatesThenUpdatesSameReminderAndOverride() throws Exception {
        putDate("2026-11-20").andExpect(status().isOk()).andExpect(jsonPath("$.hasAppointment").value(true))
                .andExpect(jsonPath("$.appointmentDate").value("2026-11-20"))
                .andExpect(jsonPath("$.predictedDate").value("2026-11-20"));
        var first = reminders.findAll().get(0);
        assertTrue(first.getId().matches("rem-[0-9a-f]{8}"));
        assertEquals("Light bulb replacement (Unit A101)", first.getTitle());
        assertEquals("Light bulb replacement", first.getCategory());
        assertEquals("None", first.getFrequency()); assertNull(first.getLastTriggered());
        putDate("2026-11-21").andExpect(status().isOk());
        assertEquals(1, reminders.count()); assertEquals(first.getId(), reminders.findAll().get(0).getId());
        assertEquals("2026-11-21", reminders.findAll().get(0).getNextDueDate());
        assertEquals(LocalDate.of(2026, 11, 21), prediction().getOverrideDate());
        mvc.perform(get("/api/reminders")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[0].nextDueDate").value("2026-11-21"));
    }

    @Test void twoConcurrentRequestsCreateOnlyOneReminder() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var one = pool.submit(() -> { start.await(); return putDate("2026-11-20").andReturn().getResponse().getStatus(); });
            var two = pool.submit(() -> { start.await(); return putDate("2026-11-21").andReturn().getResponse().getStatus(); });
            start.countDown();
            assertEquals(200, one.get(20, TimeUnit.SECONDS)); assertEquals(200, two.get(20, TimeUnit.SECONDS));
            assertEquals(1, reminders.count()); assertEquals(1, predictions.count());
            assertEquals(prediction().getOverrideDate().toString(), reminders.findAll().get(0).getNextDueDate());
        } finally { pool.shutdownNow(); }
    }

    @ParameterizedTest @ValueSource(strings = {"2026-11-07", "2026-02-30", "2026-1-09", "20/11/2026", "2026-11-20T00:00:00Z"})
    void invalidDatesAreRejectedWithoutWrites(String date) throws Exception {
        putDate(date).andExpect(status().isBadRequest());
        assertEquals(0, reminders.count()); assertEquals(0, predictions.count());
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "null", "{", "{\"date\":null}", "{\"date\":1}", "{\"date\":\"2026-11-20\",\"note\":\"ignored?\"}"})
    void invalidBodiesAreRejected(String value) throws Exception {
        mvc.perform(put(PATH).session(admin).contentType(MediaType.APPLICATION_JSON).content(value)).andExpect(status().isBadRequest());
        assertEquals(0, reminders.count());
    }
    @Test void todayIsAllowedAndTargetsAreValidated() throws Exception {
        putDate(TODAY.toString()).andExpect(status().isOk());
        for (var path : List.of("/missing/mtt-light/appointment", "/rm-A101/missing/appointment")) {
            mvc.perform(put(BASE + path).session(admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"date\":\"2026-11-20\"}")).andExpect(status().isNotFound());
            mvc.perform(delete(BASE + path).session(admin)).andExpect(status().isNotFound());
        }
        mvc.perform(put(BASE + "/rm-A101/mtt-general/appointment").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-11-20\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete(BASE + "/rm-A101/mtt-general/appointment").session(admin)).andExpect(status().isBadRequest());
    }
    @Test void appointmentEndpointsRequireAdmin() throws Exception {
        var customer = new MockHttpSession(); customer.setAttribute("userEmail", "customer@example.com");
        users.saveAndFlush(new AppUser("usr-customer-test", "customer@example.com", "test", "Resident", "", "customer"));
        for (var session : List.of(new MockHttpSession(), customer)) {
            int expected = session == customer ? 403 : 401;
            mvc.perform(put(PATH).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-11-20\"}"))
                    .andExpect(status().is(expected));
            mvc.perform(delete(PATH).session(session)).andExpect(status().is(expected));
        }
        assertEquals(0, reminders.count());
    }
    @Test void deleteDeactivatesAllMatchingRemindersAndIsIdempotent() throws Exception {
        putDate("2026-11-20").andExpect(status().isOk());
        reminder("rem-manual", "Light bulb replacement", "2026-11-19", true);
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(delete(PATH).session(admin)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasAppointment").value(false))
                    .andExpect(jsonPath("$.appointmentDate").isEmpty());
        }
        assertEquals(2, reminders.count()); assertTrue(reminders.findAll().stream().noneMatch(ScheduledReminder::getIsActive));
        assertNull(prediction().getOverrideDate()); assertNull(prediction().getPredictedDate());
    }
    @Test void deletingWithoutAnAppointmentIsHarmless() throws Exception {
        mvc.perform(delete(PATH).session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.hasAppointment").value(false));
        assertEquals(0, reminders.count());
    }
    @Test void manualReminderCountsAndGetChoosesEarliestActiveWithoutSideEffects() throws Exception {
        predictor.recalculate("rm-A101", "mtt-light");
        mvc.perform(post("/api/reminders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"rem-user\",\"title\":\"Manual\",\"category\":\"Light bulb replacement\",\"roomId\":\"rm-A101\",\"nextDueDate\":\"2026-11-25\",\"frequency\":\"Monthly\"}"))
                .andExpect(status().isOk());
        reminder("rem-earliest", "Light bulb replacement", "2026-11-20", true);
        reminder("rem-inactive", "Light bulb replacement", "2026-11-10", false);
        reminder("rem-null", "Light bulb replacement", null, true);
        reminder("rem-case", "light bulb replacement", "2026-11-09", true);
        var before = prediction().getComputedAt();
        var result = mvc.perform(get(BASE).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.predictions[0].hasAppointment").value(true))
                .andExpect(jsonPath("$.predictions[0].appointmentDate").value("2026-11-20")).andReturn();
        Set<String> fields = new HashSet<>(); body(result).at("/predictions/0").fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("roomId", "roomNumber", "buildingId", "buildingName", "taskTypeId", "taskTypeNameTh", "taskTypeNameEn",
                "predictedDate", "daysRemaining", "status", "hasAppointment", "appointmentDate"), fields);
        assertEquals(before, prediction().getComputedAt()); assertEquals(5, reminders.count());
        assertEquals("Monthly", reminders.findById("rem-user").orElseThrow().getFrequency());
    }
    @Test void putReusesInactiveRowAndConsolidatesLegacyActiveDuplicates() throws Exception {
        reminder("rem-one", "Light bulb replacement", "2026-11-19", true);
        reminder("rem-two", "Light bulb replacement", "2026-11-20", true);
        putDate("2026-11-22").andExpect(status().isOk()).andExpect(jsonPath("$.appointmentDate").value("2026-11-22"));
        assertEquals(2, reminders.count()); assertEquals(1, reminders.findAll().stream().filter(ScheduledReminder::getIsActive).count());
        mvc.perform(delete(PATH).session(admin)).andExpect(status().isOk());
        putDate("2026-11-23").andExpect(status().isOk()); assertEquals(2, reminders.count());
    }
    @Test void completionClearsOverrideAndMovesExistingReminderToNextCycle() throws Exception {
        putDate("2026-11-20").andExpect(status().isOk());
        var reminderId = reminders.findAll().get(0).getId();
        task("Pending"); changeTask("Completed");
        assertNull(prediction().getOverrideDate()); assertEquals(TODAY.plusDays(365), prediction().getPredictedDate());
        var reminder = reminders.findById(reminderId).orElseThrow();
        assertEquals(TODAY.toString(), reminder.getLastTriggered());
        assertEquals(TODAY.plusDays(365).toString(), reminder.getNextDueDate()); assertTrue(reminder.getIsActive());
        assertEquals(1, reminders.count());
        // Editing an already Completed task does not close another cycle.
        putDate("2026-11-24").andExpect(status().isOk()); changeTask("Completed");
        assertEquals(LocalDate.of(2026, 11, 24), prediction().getOverrideDate());
    }
    @Test void reversalAndCancellationLeaveAppointmentAndOverrideUntouched() throws Exception {
        task("Completed"); putDate("2026-11-20").andExpect(status().isOk());
        changeTask("In Progress"); changeTask("Cancelled");
        assertEquals(LocalDate.of(2026, 11, 20), prediction().getOverrideDate());
        assertEquals("2026-11-20", reminders.findAll().get(0).getNextDueDate());
        assertTrue(reminders.findAll().get(0).getIsActive());
    }
    @Test void completionFailureRollsBackOnlyAppointmentChangesAndKeepsMaintenanceWrite() throws Exception {
        putDate("2026-11-20").andExpect(status().isOk()); task("Pending");
        var type = types.findById("mtt-light").orElseThrow(); type.setDefaultIntervalDays(null); types.saveAndFlush(type);
        changeTask("Completed");
        assertEquals("Completed", tasks.findById("mt-close").orElseThrow().getStatus());
        assertEquals(TODAY, logs.findByTaskNo("MNT-CLOSE").get(0).getCompletedDate());
        assertEquals(LocalDate.of(2026, 11, 20), prediction().getOverrideDate());
        assertEquals("2026-11-20", reminders.findAll().get(0).getNextDueDate());
        assertNull(reminders.findAll().get(0).getLastTriggered());
    }
    @Test void appointmentFailureRollsBackReminderAndOverrideTogether() throws Exception {
        var type = types.findById("mtt-light").orElseThrow(); type.setDefaultIntervalDays(null); types.saveAndFlush(type);
        // Override wins even with invalid defaults; clearing it must fail atomically.
        putDate("2026-11-20").andExpect(status().isOk());
        assertThrows(Exception.class, () -> mvc.perform(delete(PATH).session(admin)));
        assertTrue(reminders.findAll().get(0).getIsActive());
        assertEquals(LocalDate.of(2026, 11, 20), prediction().getOverrideDate());
    }
    @Test void captureActualAppointmentExamples() throws Exception {
        var put = putDate("2026-11-20").andExpect(status().isOk()).andReturn();
        var get = mvc.perform(get(BASE).session(admin)).andExpect(status().isOk()).andReturn();
        var delete = mvc.perform(delete(PATH).session(admin)).andExpect(status().isOk()).andReturn();
        Files.writeString(Path.of("target", "phase5-appointment-examples.txt"),
                "PUT " + PATH + "\n{\"date\":\"2026-11-20\"}\n" + body(put)
                + "\nGET " + BASE + "\n" + body(get)
                + "\nDELETE " + PATH + "\n" + body(delete) + "\n");
    }
}
