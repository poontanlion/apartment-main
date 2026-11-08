package com.victory.apartment.service.maintenancepredictor;

import com.victory.apartment.TestcontainersConfiguration;
import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.seed.prediction-demo=true")
@ActiveProfiles({"test", "prediction-demo"})
@Import({TestcontainersConfiguration.class, DemoPredictionSeederIntegrationTest.Fixtures.class})
class DemoPredictionSeederIntegrationTest {
    static class DemoClock extends Clock {
        final AtomicReference<Instant> now; final ZoneId zone;
        DemoClock() { this(new AtomicReference<>(Instant.parse("2026-11-07T18:00:00Z")), ZoneOffset.UTC); }
        DemoClock(AtomicReference<Instant> now, ZoneId zone) { this.now = now; this.zone = zone; }
        public ZoneId getZone() { return zone; }
        public Clock withZone(ZoneId zone) { return new DemoClock(now, zone); }
        public Instant instant() { return now.get(); }
    }
    @TestConfiguration static class Fixtures {
        @Bean @Primary DemoClock demoClock() { return new DemoClock(); }
        @Bean @Order(-100) ApplicationRunner existingRoomFixtures(RoomRepository rooms, LeaseRepository leases, MaintenanceLogRepository logs) {
            return args -> {
                for (int i = 1; i <= 9; i++) {
                    var room = new Room(); room.setId("rm-A10" + i); room.setRoomNumber("A10" + i); room.setFloor(1);
                    room.setStatus(i <= 2 ? "Occupied" : i == 3 ? "Maintenance" : "Available"); rooms.saveAndFlush(room);
                    if (i <= 2) {
                        var lease = new Lease(); lease.setId("lease-" + i); lease.setRoomId(room.getId()); lease.setTenantId("tenant-" + i);
                        lease.setStatus("Active"); lease.setCheckInDate(LocalDate.of(2025, 1, 1)); lease.setCheckOutDate(LocalDate.of(2030, 1, 1)); leases.saveAndFlush(lease);
                    }
                }
                for (String id : List.of("real-log", "log-demo-pred-protected")) {
                    var log = new MaintenanceLog(); log.setId(id); log.setRoomId("rm-A109"); log.setDescription("Real history, keep exactly");
                    log.setTaskTypeId("mtt-light"); log.setCompletedDate(LocalDate.of(2025, 12, 1)); log.setDate("2025-12-01"); logs.saveAndFlush(log);
                }
            };
        }
    }
    @Autowired DemoPredictionSeeder seeder;
    @Autowired DemoClock clock;
    @Autowired MaintenanceLogRepository logs;
    @Autowired LeaseRepository leases;
    @Autowired RoomRepository rooms;
    @Autowired MaintenancePredictionRepository predictions;
    @Autowired AppNotificationRepository notifications;

    @BeforeEach void resetClock() { clock.now.set(Instant.parse("2026-11-07T18:00:00Z")); }

    private DemoPredictionSeeder.SeedResult verifyScenarios(boolean reset) {
        var result = seeder.seed(reset); assertFalse(result.failed()); assertEquals(8, result.scenarios().size()); assertEquals(15, result.demoLogs());
        var expected = Map.of("aircon-six", PredictionStatus.OVERDUE, "light-one", PredictionStatus.DUE_SOON,
                "electrical-two", PredictionStatus.WITHIN_30, "plumbing-volatile", PredictionStatus.WITHIN_30,
                "aircon-one", PredictionStatus.ON_TRACK, "light-two", PredictionStatus.ON_TRACK, "no-history-no-lease", PredictionStatus.NO_DATA);
        var today = LocalDate.now(clock.withZone(PredictionConfig.ZONE));
        for (var scenario : result.scenarios()) {
            var row = predictions.findByRoomIdAndTaskTypeId(scenario.roomId(), scenario.taskTypeId()).orElseThrow();
            assertEquals(scenario.status(), PredictionStatus.from(row.getPredictedDate(), today));
            if (expected.containsKey(scenario.scenario())) assertEquals(expected.get(scenario.scenario()), scenario.status());
            assertNull(row.getOverrideDate()); assertTrue(rooms.existsById(scenario.roomId()));
            if (scenario.scenario().equals("no-history-active-lease")) {
                assertEquals(LocalDate.of(2026, 1, 1), row.getPredictedDate());
                assertNotEquals(today.plusDays(365), row.getPredictedDate());
            }
        }
        return result;
    }
    @Test void seedsAllCasesAndRefreshesWithOneDigest() throws Exception {
        var result = verifyScenarios(true);
        assertEquals(1, notifications.findAll().stream().filter(n -> "maint-pred-digest:2026-11-08".equals(n.getDedupeKey())).count());
        StringBuilder evidence = new StringBuilder("scenario,roomId,taskTypeId,historyCount,status,predictedDate\n");
        result.scenarios().forEach(s -> evidence.append(s.scenario()).append(',').append(s.roomId()).append(',').append(s.taskTypeId())
                .append(',').append(s.historyCount()).append(',').append(s.status()).append(',').append(s.predictedDate()).append('\n'));
        Files.writeString(Path.of("target", "phase7-demo-statuses.csv"), evidence.toString());
    }
    @Test void repeatedRunsAndResetKeepNonDemoLogsAndLeasesExactly() {
        verifyScenarios(true); long count = logs.count();
        var before = leases.findAll().stream().map(l -> l.getId() + ":" + l.getCheckInDate() + ":" + l.getStatus()).sorted().toList();
        verifyScenarios(false); assertEquals(count, logs.count());
        var extra = new MaintenanceLog(); extra.setId("log-demo-pred-extra"); extra.setDescription("[DEMO] obsolete fixture"); logs.saveAndFlush(extra);
        verifyScenarios(true); assertFalse(logs.existsById(extra.getId())); assertEquals(count, logs.count());
        for (String id : List.of("real-log", "log-demo-pred-protected")) {
            var row = logs.findById(id).orElseThrow(); assertEquals("Real history, keep exactly", row.getDescription());
            assertEquals(LocalDate.of(2025, 12, 1), row.getCompletedDate());
        }
        assertEquals(before, leases.findAll().stream().map(l -> l.getId() + ":" + l.getCheckInDate() + ":" + l.getStatus()).sorted().toList());
    }
    @ParameterizedTest @ValueSource(strings = {"2026-03-01", "2026-06-30", "2026-07-01", "2026-12-31", "2028-02-29"})
    void targetStatusesSurviveSeasonAndCalendarChanges(String date) {
        clock.now.set(LocalDate.parse(date).atTime(1, 0).atZone(PredictionConfig.ZONE).toInstant());
        verifyScenarios(true);
    }
}
