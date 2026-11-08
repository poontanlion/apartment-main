package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.victory.apartment.TestcontainersConfiguration;
import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
import com.victory.apartment.service.maintenancepredictor.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MaintenancePredictionIntegrationTest.FixedTime.class})
class MaintenancePredictionIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);
    private static final String BASE = "/api/maintenance/predictions";
    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock predictionApiClock() {
            return Clock.fixed(Instant.parse("2026-11-07T18:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RoomRepository rooms;
    @Autowired MaintenanceTaskTypeRepository types;
    @Autowired MaintenanceLogRepository logs;
    @Autowired MaintenanceTaskRepository tasks;
    @Autowired LeaseRepository leases;
    @Autowired ScheduledReminderRepository reminders;
    @Autowired AppUserRepository users;
    @Autowired EntityManager entityManager;
    @SpyBean MaintenancePredictionRepository predictions;
    @SpyBean MaintenancePredictorService predictor;
    private MockHttpSession admin;

    @BeforeEach void setup() {
        admin = new MockHttpSession();
        admin.setAttribute("userEmail", "prediction-admin@example.com");
        users.save(new AppUser("usr-pred-admin", "prediction-admin@example.com", "test-password", "Admin", "", "admin"));
        users.save(new AppUser("usr-pred-customer", "prediction-customer@example.com", "test-password", "Resident", "", "customer"));
        room("rm-A101", "A101", "bld-A", "Victory North");
        room("rm-A102", "A102", "bld-B", "Victory South");
        prediction("rm-A101", "mtt-light", TODAY.minusDays(1));
        prediction("rm-A101", "mtt-aircon", TODAY);
        prediction("rm-A101", "mtt-electrical", TODAY.plusDays(7));
        prediction("rm-A101", "mtt-plumbing", TODAY.plusDays(8));
        prediction("rm-A102", "mtt-light", TODAY.plusDays(30));
        prediction("rm-A102", "mtt-aircon", TODAY.plusDays(31));
        prediction("rm-A102", "mtt-electrical", null);
        prediction("rm-A102", "mtt-general", TODAY.minusDays(10));
    }

    @AfterEach void cleanup() {
        // Restore spy behavior before deleting fixtures, including after a failing assertion.
        reset(predictions, predictor);
        predictions.deleteAll(); logs.deleteAll(); tasks.deleteAll(); leases.deleteAll();
        reminders.deleteAll(); rooms.deleteAll(); users.deleteAll();
        var electrical = types.findById("mtt-electrical").orElseThrow();
        electrical.setDefaultIntervalDays(365); types.saveAndFlush(electrical);
    }

    private void room(String id, String number, String buildingId, String buildingName) {
        var room = new Room(); room.setId(id); room.setRoomNumber(number); room.setFloor(1);
        room.setBuildingId(buildingId); room.setBuildingName(buildingName); rooms.saveAndFlush(room);
    }
    private void prediction(String room, String type, LocalDate date) {
        var p = new MaintenancePrediction(); p.setId("pred-test-" + room + "-" + type);
        p.setRoomId(room); p.setTaskTypeId(type); p.setPredictedDate(date);
        p.setComputedAt(LocalDateTime.of(2020, 1, 1, 0, 0)); predictions.saveAndFlush(p);
    }
    private JsonNode read(String query) throws Exception {
        return json.readTree(mvc.perform(get(java.net.URI.create(BASE + query)).session(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    private ResultActions override(String body) throws Exception {
        return mvc.perform(patch(BASE + "/rm-A101/mtt-light").session(admin)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private void changeTask(String id, String status) throws Exception {
        mvc.perform(put("/api/maintenance-tasks/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", status))))
                .andExpect(status().isOk());
    }
    private MaintenanceTask task(String category, String status) throws Exception {
        var task = new MaintenanceTask(); task.setId("mt-trigger"); task.setTaskNo("MNT-TRIGGER");
        task.setRoomId("rm-A101"); task.setRoomNumber("A101"); task.setCategory(category); task.setStatus(status);
        // Deliberately unrelated to Bangkok today, to catch accidental completedAt/date use.
        task.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        task.setCompletedAt(LocalDateTime.of(2026, 2, 1, 23, 0));
        mvc.perform(post("/api/maintenance-tasks").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(task))).andExpect(status().isOk());
        return tasks.findById(task.getId()).orElseThrow();
    }

    @Test void listIsReadOnlySortedAndHasOnlyPublicFields() throws Exception {
        clearInvocations(predictions, predictor);
        var response = read("");
        var rows = response.get("predictions");
        assertEquals(7, rows.size());
        assertEquals("overdue", rows.get(0).get("status").asText());
        assertEquals(-1, rows.get(0).get("daysRemaining").asInt());
        assertEquals("no_data", rows.get(6).get("status").asText());
        assertTrue(rows.get(6).get("predictedDate").isNull());
        assertTrue(rows.get(6).get("daysRemaining").isNull());
        Set<String> fields = Set.of("roomId", "roomNumber", "buildingId", "buildingName", "taskTypeId",
                "taskTypeNameTh", "taskTypeNameEn", "predictedDate", "daysRemaining", "status", "hasAppointment");
        for (JsonNode row : rows) {
            Set<String> actual = new HashSet<>(); row.fieldNames().forEachRemaining(actual::add);
            assertEquals(fields, actual); assertFalse(row.get("hasAppointment").asBoolean());
        }
        assertEquals(1, response.at("/summary/overdue").asInt());
        assertEquals(2, response.at("/summary/dueSoon").asInt());
        assertEquals(4, response.at("/summary/within30").asInt());
        assertEquals(7, response.at("/summary/total").asInt());
        verifyNoInteractions(predictor);
        verify(predictions, times(1)).findAll();
        verifyNoMoreInteractions(predictions);
    }

    @Test void filtersApplyBeforeSummaryExceptStatus() throws Exception {
        assertEquals(4, read("?roomId=rm-A101").at("/summary/total").asInt());
        assertEquals(2, read("?taskTypeId=mtt-light").at("/summary/total").asInt());
        assertEquals(3, read("?buildingId=bld-B").at("/summary/total").asInt());
        var filtered = read("?status=due_soon,within_30&buildingId=bld-A");
        assertEquals(3, filtered.get("predictions").size());
        assertEquals(4, filtered.at("/summary/total").asInt());
        assertEquals(1, filtered.at("/summary/overdue").asInt());
        assertEquals(2, filtered.at("/summary/dueSoon").asInt());
        assertEquals(3, filtered.at("/summary/within30").asInt());
        assertEquals(0, read("?roomId=missing").at("/summary/total").asInt());
    }

    @ParameterizedTest @ValueSource(strings = {"overdue", "due_soon", "within_30", "on_track", "no_data"})
    void singleStatusFilter(String value) throws Exception {
        var response = read("?status=" + value);
        for (JsonNode row : response.get("predictions")) assertEquals(value, row.get("status").asText());
        assertEquals(7, response.at("/summary/total").asInt());
    }

    @ParameterizedTest @ValueSource(strings = {"unknown", "due_soon,unknown", "", "overdue,"})
    void unknownOrEmptyStatusIsBadRequest(String value) throws Exception {
        mvc.perform(get(BASE).session(admin).param("status", value)).andExpect(status().isBadRequest());
    }

    @Test void searchMatchesRoomBuildingAndBothTaskLanguagesIgnoringCase() throws Exception {
        assertEquals(4, read("?q=a101").at("/summary/total").asInt());
        assertEquals(3, read("?q=sOuTh").at("/summary/total").asInt());
        assertEquals(2, read("?q=LIGHT%20BULB").at("/summary/total").asInt());
        var thai = mvc.perform(get(BASE).session(admin).param("q", "หลอดไฟ"))
                .andExpect(status().isOk()).andReturn();
        assertEquals(2, json.readTree(thai.getResponse().getContentAsString(StandardCharsets.UTF_8)).at("/summary/total").asInt());
        assertEquals(0, read("?q=unmatched").at("/summary/total").asInt());
    }

    @Test void allNewEndpointsRequireAdmin() throws Exception {
        var customer = new MockHttpSession(); customer.setAttribute("userEmail", "prediction-customer@example.com");
        for (MockHttpSession session : List.of(new MockHttpSession(), customer)) {
            int expected = session == customer ? 403 : 401;
            mvc.perform(get(BASE).session(session)).andExpect(status().is(expected));
            mvc.perform(post(BASE + "/refresh").session(session)).andExpect(status().is(expected));
            mvc.perform(patch(BASE + "/rm-A101/mtt-light").session(session)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"overrideDate\":null}"))
                    .andExpect(status().is(expected));
        }
        var unknown = new MockHttpSession(); unknown.setAttribute("userEmail", "unknown@example.com");
        mvc.perform(get(BASE).session(unknown)).andExpect(status().isForbidden());
    }

    @Test void configuredAdminFallbackAndDatabaseRoleAreChecked() throws Exception {
        var fallback = new MockHttpSession(); fallback.setAttribute("userEmail", "admin@victoryapartment.com");
        mvc.perform(get(BASE).session(fallback)).andExpect(status().isOk());
        users.saveAndFlush(new AppUser("usr-config-customer", "admin@victoryapartment.com", "test", "Customer", "", "customer"));
        mvc.perform(get(BASE).session(fallback)).andExpect(status().isForbidden());
    }

    @Test void overrideAndClearRecalculateImmediately() throws Exception {
        override("{\"overrideDate\":\"2026-11-08\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.predictedDate").value("2026-11-08"))
                .andExpect(jsonPath("$.daysRemaining").value(0))
                .andExpect(jsonPath("$.status").value("due_soon"))
                .andExpect(jsonPath("$.overrideDate").doesNotExist());
        assertEquals(TODAY, predictions.findByRoomIdAndTaskTypeId("rm-A101", "mtt-light").orElseThrow().getOverrideDate());
        override("{\"overrideDate\":null}").andExpect(status().isOk())
                .andExpect(jsonPath("$.predictedDate").isEmpty()).andExpect(jsonPath("$.status").value("no_data"));
        assertNull(predictions.findByRoomIdAndTaskTypeId("rm-A101", "mtt-light").orElseThrow().getOverrideDate());
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"overrideDate\":\"2026-11-07\"}", "{\"overrideDate\":\"08/11/2026\"}",
            "{\"overrideDate\":\"2027-02-30\"}", "{\"overrideDate\":\"2026-11-08T00:00:00\"}",
            "{\"overrideDate\":123}", "{}", "null", "{", "{\"overrideDate\":\"2026-1-9\"}"})
    void invalidOverrideDoesNotWrite(String body) throws Exception {
        override(body).andExpect(status().isBadRequest());
        assertEquals(TODAY.minusDays(1), predictions.findByRoomIdAndTaskTypeId("rm-A101", "mtt-light").orElseThrow().getPredictedDate());
    }

    @Test void overrideTargetValidation() throws Exception {
        for (String path : List.of("/missing/mtt-light", "/rm-A101/missing"))
            mvc.perform(patch(BASE + path).session(admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"overrideDate\":null}")).andExpect(status().isNotFound());
        mvc.perform(patch(BASE + "/rm-A101/mtt-general").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"overrideDate\":null}")).andExpect(status().isBadRequest());
    }

    @Test void refreshReportsCountsAndContinuesAfterFailedPairs() throws Exception {
        predictions.deleteAll();
        rooms.deleteById("rm-A102");
        var electrical = types.findById("mtt-electrical").orElseThrow();
        electrical.setDefaultIntervalDays(null); types.saveAndFlush(electrical);
        mvc.perform(post(BASE + "/refresh").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(3)).andExpect(jsonPath("$.failure").value(1));
        assertEquals(3, predictions.count());
        assertTrue(predictions.findByRoomIdAndTaskTypeId("rm-A101", "mtt-light").isPresent());
        assertTrue(predictions.findByRoomIdAndTaskTypeId("rm-A101", "mtt-plumbing").isPresent());
    }

    @Test void completedTransitionUsesThaiClockAndReversalClearsHistory() throws Exception {
        task("Light bulb replacement", "Pending");
        changeTask("mt-trigger", "Completed");
        var log = logs.findByTaskNo("MNT-TRIGGER").get(0);
        assertEquals(TODAY, log.getCompletedDate());
        assertEquals("mtt-light", log.getTaskTypeId());
        assertEquals(TODAY.plusDays(365), predictions.findByRoomIdAndTaskTypeId("rm-A101", "mtt-light").orElseThrow().getPredictedDate());
        changeTask("mt-trigger", "In Progress");
        assertNull(logs.findByTaskNo("MNT-TRIGGER").get(0).getCompletedDate());
        assertNull(predictions.findByRoomIdAndTaskTypeId("rm-A101", "mtt-light").orElseThrow().getPredictedDate());
        changeTask("mt-trigger", "Completed");
        changeTask("mt-trigger", "Cancelled");
        assertNull(logs.findByTaskNo("MNT-TRIGGER").get(0).getCompletedDate());
        assertNull(predictions.findByRoomIdAndTaskTypeId("rm-A101", "mtt-light").orElseThrow().getPredictedDate());
    }

    @Test void createdCompletedTaskUsesThaiTodayAndSubsequentSyncPreservesIt() throws Exception {
        task("Light bulb replacement", "Completed");
        assertEquals(TODAY, logs.findByTaskNo("MNT-TRIGGER").get(0).getCompletedDate());
        clearInvocations(predictor);
        mvc.perform(get("/api/maintenance-logs")).andExpect(status().isOk());
        assertEquals(TODAY, logs.findByTaskNo("MNT-TRIGGER").get(0).getCompletedDate());
        verifyNoInteractions(predictor);
    }

    @Test void legacyGetSyncUsesCompletedAtDateWithoutRecalculation() throws Exception {
        var task = new MaintenanceTask(); task.setId("mt-legacy"); task.setTaskNo("MNT-LEGACY");
        task.setRoomId("rm-A101"); task.setCategory("Light bulb replacement"); task.setStatus("Completed");
        task.setCompletedAt(LocalDateTime.of(2025, 12, 31, 23, 30)); tasks.saveAndFlush(task);
        clearInvocations(predictor);
        mvc.perform(get("/api/maintenance-logs")).andExpect(status().isOk());
        assertEquals(LocalDate.of(2025, 12, 31), logs.findByTaskNo("MNT-LEGACY").get(0).getCompletedDate());
        verifyNoInteractions(predictor);
        assertEquals(TODAY.minusDays(1), predictions.findByRoomIdAndTaskTypeId("rm-A101", "mtt-light").orElseThrow().getPredictedDate());
    }

    @ParameterizedTest @ValueSource(strings = {"General Repair", "light bulb replacement"})
    void generalRepairAndUnmappedCategoriesDoNotPredict(String category) throws Exception {
        predictions.deleteAll();
        task(category, "Pending"); changeTask("mt-trigger", "Completed");
        assertEquals(0, predictions.count());
        var log = logs.findByTaskNo("MNT-TRIGGER").get(0);
        assertEquals(TODAY, log.getCompletedDate());
        assertEquals(category.equals("General Repair") ? "mtt-general" : null, log.getTaskTypeId());
    }

    @ParameterizedTest @ValueSource(strings = {"2026-02-30", "08/11/2026", "2026-1-01", "invalid", "2026-11-08"})
    void manualLogDatesAreStrictAndDerivedFieldsCannotBeForged(String date) throws Exception {
        var body = Map.of("roomId", "rm-A101", "category", "Light bulb replacement", "date", date,
                "completedDate", "2000-01-01", "taskTypeId", "mtt-aircon");
        var result = mvc.perform(post("/api/maintenance-logs").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn();
        var response = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("mtt-light", response.get("taskTypeId").asText());
        if (date.equals("2026-11-08")) {
            assertEquals(date, response.get("completedDate").asText());
            assertEquals(TODAY.plusDays(365), predictions.findByRoomIdAndTaskTypeId("rm-A101", "mtt-light").orElseThrow().getPredictedDate());
        } else assertTrue(response.get("completedDate").isNull());
    }

    @Test void failedRecalculationDoesNotRollBackMaintenanceWrite() throws Exception {
        task("Light bulb replacement", "Pending");
        doThrow(new IllegalStateException("simulated predictor failure")).when(predictor).recalculate("rm-A101", "mtt-light");
        changeTask("mt-trigger", "Completed");
        assertEquals("Completed", tasks.findById("mt-trigger").orElseThrow().getStatus());
        assertEquals(TODAY, logs.findByTaskNo("MNT-TRIGGER").get(0).getCompletedDate());
        mvc.perform(post("/api/maintenance-logs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"rm-A101\",\"category\":\"Light bulb replacement\",\"date\":\"2026-11-08\"}"))
                .andExpect(status().isOk());
        assertEquals(2, logs.count());
    }

    @Test void categoryChangeRecalculatesOldAndNewType() throws Exception {
        task("Light bulb replacement", "Completed");
        mvc.perform(put("/api/maintenance-tasks/mt-trigger").contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"Plumbing\"}")).andExpect(status().isOk());
        assertNull(predictions.findByRoomIdAndTaskTypeId("rm-A101", "mtt-light").orElseThrow().getPredictedDate());
        assertEquals(TODAY.plusDays(365), predictions.findByRoomIdAndTaskTypeId("rm-A101", "mtt-plumbing").orElseThrow().getPredictedDate());
    }

    @Test void existingLogUpdatesAndRemindersStillWork() throws Exception {
        var result = mvc.perform(post("/api/maintenance-logs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"rm-A101\",\"category\":\"Light bulb replacement\",\"date\":\"2026-01-01\"}"))
                .andExpect(status().isOk()).andReturn();
        String id = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asText();
        mvc.perform(put("/api/maintenance-logs/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Updated description\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Updated description"));
        mvc.perform(post("/api/reminders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"rem-test\",\"title\":\"Reminder\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(true));
        mvc.perform(put("/api/reminders/rem-test").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated reminder\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/reminders/rem-test/toggle")).andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
        mvc.perform(get("/api/reminders")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Updated reminder"));
    }

    @Test void concurrentInsertRetriesOnceAndKeepsOverride() throws Exception {
        predictions.deleteAll();
        var barrier = new CyclicBarrier(2);
        var calls = new AtomicInteger();
        doAnswer(invocation -> {
            var rows = entityManager.createQuery("select p from MaintenancePrediction p where p.roomId = :room and p.taskTypeId = :type", MaintenancePrediction.class)
                    .setParameter("room", "rm-A101").setParameter("type", "mtt-light").getResultList();
            var result = rows.stream().findFirst();
            if (calls.incrementAndGet() <= 2) barrier.await(15, TimeUnit.SECONDS);
            return result;
        }).when(predictions).findByRoomIdAndTaskTypeId("rm-A101", "mtt-light");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> predictor.recalculate("rm-A101", "mtt-light"));
            var second = pool.submit(() -> predictor.setOverride("rm-A101", "mtt-light", TODAY.plusDays(10)));
            assertTrue(first.get(25, TimeUnit.SECONDS).isPresent());
            assertTrue(second.get(25, TimeUnit.SECONDS).isPresent());
            assertEquals(3, calls.get()); // two initial lookups and exactly one retry
            reset(predictions);
            var row = predictions.findByRoomIdAndTaskTypeId("rm-A101", "mtt-light").orElseThrow();
            assertEquals(TODAY.plusDays(10), row.getOverrideDate());
            assertEquals(TODAY.plusDays(10), row.getPredictedDate());
            assertEquals(1, predictions.count());
        } finally { pool.shutdownNow(); }
    }

    @Test void captureActualRequestsAndResponses() throws Exception {
        var get = mvc.perform(get(BASE).session(admin).param("roomId", "rm-A101").param("taskTypeId", "mtt-light"))
                .andExpect(status().isOk()).andReturn();
        var refresh = mvc.perform(post(BASE + "/refresh").session(admin)).andExpect(status().isOk()).andReturn();
        var patch = override("{\"overrideDate\":\"2026-11-20\"}").andExpect(status().isOk()).andReturn();
        String examples = "GET " + BASE + "?roomId=rm-A101&taskTypeId=mtt-light\n" + get.getResponse().getContentAsString(StandardCharsets.UTF_8)
                + "\nPOST " + BASE + "/refresh\n" + refresh.getResponse().getContentAsString(StandardCharsets.UTF_8)
                + "\nPATCH " + BASE + "/rm-A101/mtt-light\n{\"overrideDate\":\"2026-11-20\"}\n"
                + patch.getResponse().getContentAsString(StandardCharsets.UTF_8) + "\n";
        Files.writeString(Path.of("target", "phase3-api-examples.txt"), examples);
    }
}
