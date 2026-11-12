package com.victory.apartment.service.maintenancepredictor;

import com.victory.apartment.TestcontainersConfiguration;
import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MaintenancePredictorIntegrationTest.FixedTime.class})
class MaintenancePredictorIntegrationTest {
    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock predictionTestClock() {
            return Clock.fixed(Instant.parse("2026-11-07T18:00:00Z"), PredictionConfig.ZONE);
        }
    }

    @Autowired MaintenancePredictorService service;
    @Autowired RoomRepository rooms;
    @Autowired LeaseRepository leases;
    @Autowired MaintenanceLogRepository logs;
    @Autowired MaintenancePredictionRepository predictions;

    @BeforeEach void room() {
        var room = new Room();
        room.setId("rm-P101"); room.setRoomNumber("P101"); room.setFloor(1);
        rooms.saveAndFlush(room);
    }

    @AfterEach void cleanup() {
        predictions.deleteAll();
        logs.deleteAll();
        leases.deleteAll();
        rooms.deleteAll();
    }

    private void lease(String id, String status, LocalDate start) {
        var row = new Lease();
        row.setId(id); row.setRoomId("rm-P101"); row.setTenantId("test-tenant");
        row.setStatus(status); row.setCheckInDate(start); row.setCheckOutDate(start.plusYears(1));
        leases.save(row);
    }

    @Test void persistedUpsertKeepsOverrideAndOriginalId() {
        var log = new MaintenanceLog();
        log.setId("log-predictor"); log.setRoomId("rm-P101");
        log.setTaskTypeId("mtt-light"); log.setCompletedDate(LocalDate.of(2026, 1, 1));
        log.setDate("invalid legacy date must not be parsed");
        logs.saveAndFlush(log);
        var initial = service.recalculate("rm-P101", "mtt-light").orElseThrow();
        assertEquals(LocalDate.of(2027, 1, 1), initial.getPredictedDate());
        var id = initial.getId();
        var override = LocalDate.of(2026, 11, 20);
        initial.setOverrideDate(override);
        predictions.saveAndFlush(initial);
        service.recalculate("rm-P101", "mtt-light");
        var reloaded = predictions.findByRoomIdAndTaskTypeId("rm-P101", "mtt-light").orElseThrow();
        assertEquals(id, reloaded.getId());
        assertEquals(override, reloaded.getOverrideDate());
        assertEquals(override, reloaded.getPredictedDate());
        assertEquals(LocalDateTime.of(2026, 11, 8, 1, 0), reloaded.getComputedAt());
        assertEquals(1, predictions.count());
    }

    @Test void usesLatestActiveLeaseNotFutureOrTerminatedLease() {
        lease("old", "Active", LocalDate.of(2025, 1, 1));
        lease("current", "Active", LocalDate.of(2026, 11, 8));
        lease("future", "Active", LocalDate.of(2026, 11, 9));
        lease("terminated", "Terminated", LocalDate.of(2026, 11, 8));
        assertEquals(LocalDate.of(2027, 11, 8), service.recalculate("rm-P101", "mtt-light").orElseThrow().getPredictedDate());
    }

    @Test void inactiveLeasesDoNotProvideFallbackAndRefreshIsRepeatable() {
        lease("inactive", "Expired", LocalDate.of(2026, 1, 1));
        var second = new Room(); second.setId("rm-P102"); second.setRoomNumber("P102"); second.setFloor(1);
        rooms.saveAndFlush(second);
        service.refreshAll(); service.refreshAll();
        assertEquals(8, predictions.count());
        assertTrue(predictions.findAll().stream().allMatch(p -> p.getPredictedDate() == null));
        assertTrue(predictions.findByRoomIdAndTaskTypeId("rm-P101", "mtt-general").isEmpty());
        assertTrue(service.recalculate("rm-P101", "mtt-general").isEmpty());
        assertTrue(service.recalculate("missing", "mtt-light").isEmpty());
    }

    @Test void refreshPreservesOverrideAndSkipsOrphanHistory() {
        var orphan = new MaintenanceLog();
        orphan.setId("log-orphan");
        orphan.setRoomId("missing-room");
        orphan.setTaskTypeId("mtt-light");
        orphan.setCompletedDate(LocalDate.of(2026, 1, 1));
        logs.saveAndFlush(orphan);
        var row = service.recalculate("rm-P101", "mtt-light").orElseThrow();
        var override = LocalDate.of(2026, 12, 1);
        row.setOverrideDate(override);
        predictions.saveAndFlush(row);

        service.refreshAll();

        var reloaded = predictions.findByRoomIdAndTaskTypeId("rm-P101", "mtt-light").orElseThrow();
        assertEquals(row.getId(), reloaded.getId());
        assertEquals(override, reloaded.getOverrideDate());
        assertEquals(override, reloaded.getPredictedDate());
        assertEquals(4, predictions.count());
        assertTrue(predictions.findByRoomIdAndTaskTypeId("missing-room", "mtt-light").isEmpty());
    }
}
