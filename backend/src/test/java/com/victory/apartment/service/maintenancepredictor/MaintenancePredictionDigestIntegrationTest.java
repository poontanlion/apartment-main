package com.victory.apartment.service.maintenancepredictor;

import com.victory.apartment.TestcontainersConfiguration;
import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MaintenancePredictionDigestIntegrationTest.TimeConfig.class})
class MaintenancePredictionDigestIntegrationTest {
    static class MutableClock extends Clock {
        final AtomicReference<Instant> instant;
        final ZoneId zone;
        MutableClock() { this(new AtomicReference<>(Instant.parse("2026-11-07T18:00:00Z")), ZoneOffset.UTC); }
        MutableClock(AtomicReference<Instant> instant, ZoneId zone) { this.instant = instant; this.zone = zone; }
        public ZoneId getZone() { return zone; }
        public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }
        public Instant instant() { return instant.get(); }
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary MutableClock digestClock() { return new MutableClock(); }
    }
    @Autowired MutableClock clock;
    @Autowired MaintenancePredictionDigestService digest;
    @Autowired MaintenancePredictorService predictor;
    @Autowired RoomRepository rooms;
    @Autowired MaintenancePredictionRepository predictions;
    @Autowired AppNotificationRepository notifications;
    @Autowired AppUserRepository users;
    @Autowired LeaseRepository leases;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired MockMvc mvc;
    final LocalDate today = LocalDate.of(2026, 11, 8);

    @BeforeEach void setup() {
        notifications.deleteAll(); users.deleteAll();
        clock.instant.set(Instant.parse("2026-11-07T18:00:00Z"));
        for (String id : List.of("rm-D101", "rm-D102")) {
            var room = new Room(); room.setId(id); room.setRoomNumber(id.substring(3)); room.setFloor(1); rooms.saveAndFlush(room);
        }
        users.saveAllAndFlush(List.of(new AppUser("admin-a", "a@example.com", "test", "A", "", "admin"),
                new AppUser("admin-b", "b@example.com", "test", "B", "", "ADMIN"),
                new AppUser("customer", "customer@example.com", "test", "Resident", "", "customer")));
        prediction("rm-D101", "mtt-light", today.minusDays(1));
        prediction("rm-D101", "mtt-aircon", today);
        prediction("rm-D101", "mtt-plumbing", today.plusDays(7));
        prediction("rm-D101", "mtt-electrical", today.plusDays(8));
        prediction("rm-D101", "mtt-general", today.minusDays(100));
        prediction("rm-D102", "mtt-light", null);
    }
    @AfterEach void cleanup() {
        notifications.deleteAll(); predictions.deleteAll(); leases.deleteAll(); rooms.deleteAll(); users.deleteAll();
    }
    private void prediction(String roomId, String type, LocalDate date) {
        var row = new MaintenancePrediction(); row.setId("p-" + roomId + type); row.setRoomId(roomId); row.setTaskTypeId(type); row.setPredictedDate(date);
        predictions.saveAndFlush(row);
    }
    @Test void digestUsesOnlyRecurringDatedUrgentPredictionsAndOneBroadcast() throws Exception {
        assertEquals(new MaintenancePredictionDigestService.DigestResult(1, 0, 0), digest.sendDailyDigest());
        var rows = notifications.findAll(); assertEquals(1, rows.size());
        assertEquals(Set.of("system"),
                rows.stream().map(AppNotification::getUserId).collect(java.util.stream.Collectors.toSet()));
        for (var row : rows) {
            assertEquals("warning", row.getType()); assertFalse(row.getIsRead());
            assertEquals("maint-pred-digest:2026-11-08", row.getDedupeKey());
            assertTrue(row.getMessage().contains("Overdue: 1 items; due soon (within 7 days): 2 items"));
            assertTrue(row.getMessage().contains("/admin/maintenance?tab=predictions"));
            for (var forbidden : List.of("interval", "confidence", "history", "weighted", "override", "computed"))
                assertFalse(row.getMessage().toLowerCase().contains(forbidden));
            assertEquals(LocalDateTime.of(2026, 11, 8, 1, 0), row.getCreatedAt());
        }
        var example = rows.get(0);
        Files.writeString(Path.of("target", "phase7-digest-example.txt"), "userId=" + example.getUserId() + "\ntitle=" + example.getTitle()
                + "\nmessage=" + example.getMessage() + "\ntype=" + example.getType() + "\ndedupe_key=" + example.getDedupeKey() + "\n");
    }
    @Test void sameDayCountChangesDoNotNotifyAgainAndNextThaiDayDoes() {
        digest.sendDailyDigest();
        var originalMessage = notifications.findAll().get(0).getMessage();
        var row = predictions.findByRoomIdAndTaskTypeId("rm-D101", "mtt-electrical").orElseThrow();
        row.setPredictedDate(today.minusDays(2)); predictions.saveAndFlush(row);
        assertEquals(new MaintenancePredictionDigestService.DigestResult(0, 1, 0), digest.sendDailyDigest());
        assertTrue(notifications.findAll().stream().allMatch(n -> n.getMessage().equals(originalMessage)));
        clock.instant.set(Instant.parse("2026-11-08T17:00:00Z"));
        assertEquals(1, digest.sendDailyDigest().created()); assertEquals(2, notifications.count());
    }
    @Test void concurrentRefreshesAndRepeatedRefreshesProduceOneBroadcastDigest() throws Exception {
        for (var room : rooms.findAll()) {
            var lease = new Lease(); lease.setId("lease-" + room.getId()); lease.setRoomId(room.getId()); lease.setStatus("Active");
            lease.setTenantId("digest-fixture-tenant"); lease.setCheckInDate(LocalDate.of(2024, 1, 1));
            lease.setCheckOutDate(LocalDate.of(2027, 1, 1)); leases.saveAndFlush(lease);
        }
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return predictor.refreshAll(); });
            var second = pool.submit(() -> { start.await(); return predictor.refreshAll(); });
            start.countDown();
            assertEquals(0, first.get(30, TimeUnit.SECONDS).failure());
            assertEquals(0, second.get(30, TimeUnit.SECONDS).failure());
            predictor.refreshAll(); assertEquals(1, notifications.count());
        } finally { pool.shutdownNow(); }
    }
    @Test void onlyNonUrgentNullAndNonRecurringPredictionsDoNotNotify() {
        for (var row : predictions.findAll()) {
            if (!row.getTaskTypeId().equals("mtt-general")) row.setPredictedDate(row.getPredictedDate() == null ? null : today.plusDays(8));
            predictions.saveAndFlush(row);
        }
        assertEquals(0, digest.sendDailyDigest().created()); assertEquals(0, notifications.count());
    }
    @Test void customerRowsDoNotBecomeRecipients() {
        users.saveAndFlush(new AppUser("config-customer", "admin@victoryapartment.com", "test", "Customer", "", "customer"));
        assertEquals(1, digest.sendDailyDigest().created());
        assertTrue(notifications.findAll().stream().noneMatch(n -> "admin@victoryapartment.com".equals(n.getUserId())));
    }
    @Test void adminIdentitiesAndAliasesDoNotMultiplyBroadcasts() {
        users.saveAndFlush(new AppUser("config-admin", "admin@victoryapartment.com", "test", "Admin", "", "admin"));
        users.saveAndFlush(new AppUser("admin-alias", "admin", "test", "Admin alias", "", "admin"));
        assertEquals(1, digest.sendDailyDigest().created()); assertEquals(1, notifications.count());
    }
    @Test void existingPerIdentityDigestsSuppressNewBroadcastWithoutDeletingLegacyRows() {
        for (String recipient : List.of("a@example.com", "admin")) {
            var legacy = new AppNotification(); legacy.setId("old-" + recipient); legacy.setUserId(recipient);
            legacy.setDedupeKey("maint-pred-digest:2026-11-08"); notifications.saveAndFlush(legacy);
        }
        assertEquals(new MaintenancePredictionDigestService.DigestResult(0, 1, 0), digest.sendDailyDigest());
        assertEquals(2, notifications.count());
        clock.instant.set(Instant.parse("2026-11-08T17:00:00Z"));
        assertEquals(1, digest.sendDailyDigest().created()); assertEquals(3, notifications.count());
    }
    @Test void testProfileDisablesAutomaticJob() {
        assertTrue(context.getBeansOfType(MaintenancePredictionRefreshJob.class).isEmpty());
    }
    @Test void legacyNotificationCrudStillWorksAndDoesNotAcceptDedupeKeys() throws Exception {
        for (String id : List.of("legacy1", "legacy2"))
            mvc.perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":\"" + id + "\",\"userId\":\"admin\",\"title\":\"Legacy\",\"message\":\"Same message\",\"type\":\"info\",\"dedupeKey\":\"must-be-ignored\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.dedupeKey").doesNotExist());
        assertTrue(notifications.findAll().stream().allMatch(n -> n.getDedupeKey() == null));
        mvc.perform(get("/api/notifications")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(put("/api/notifications/legacy1/read")).andExpect(status().isOk()).andExpect(jsonPath("$.isRead").value(true));
        mvc.perform(put("/api/notifications/mark-read")).andExpect(status().isOk());
        assertTrue(notifications.findAll().stream().allMatch(AppNotification::getIsRead));
        mvc.perform(delete("/api/notifications/legacy1")).andExpect(status().isOk());
        assertEquals(1, notifications.count());
    }
}
