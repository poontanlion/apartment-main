package com.victory.apartment.service.maintenancepredictor;

import com.victory.apartment.TestcontainersConfiguration;
import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import java.nio.file.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"app.maintenance.prediction.daily-refresh-enabled=false", "app.maintenance.prediction.daily-refresh-cron=-"})
@ActiveProfiles({"test", "maintenance-prediction-jobs"})
@Import({TestcontainersConfiguration.class, MaintenancePredictionStartupIntegrationTest.Fixtures.class})
class MaintenancePredictionStartupIntegrationTest {
    @TestConfiguration static class Fixtures {
        @Bean @Primary Clock startupTestClock() { return Clock.fixed(Instant.parse("2026-11-07T18:00:00Z"), ZoneOffset.UTC); }
        @Bean ApplicationRunner beforeReadyFixture(RoomRepository rooms, LeaseRepository leases, MaintenancePredictionRepository predictions) {
            return args -> {
                assertEquals(0, predictions.count());
                var room = new Room(); room.setId("rm-START101"); room.setRoomNumber("START101"); room.setFloor(1); rooms.saveAndFlush(room);
                var lease = new Lease(); lease.setId("lease-start"); lease.setRoomId(room.getId()); lease.setStatus("Active");
                lease.setTenantId("startup-fixture-tenant"); lease.setCheckInDate(LocalDate.of(2024, 1, 1));
                lease.setCheckOutDate(LocalDate.of(2027, 1, 1)); leases.saveAndFlush(lease);
            };
        }
    }
    @Autowired MaintenancePredictionRepository predictions;
    @Autowired AppNotificationRepository notifications;
    @Test void applicationReadyRefreshPopulatesEmptyPredictionTableAndCreatesDigest() throws Exception {
        assertEquals(4, predictions.count());
        assertTrue(predictions.findAll().stream().allMatch(p -> p.getComputedAt().equals(LocalDateTime.of(2026, 11, 8, 1, 0))));
        assertTrue(notifications.findAll().stream().anyMatch(n -> "maint-pred-digest:2026-11-08".equals(n.getDedupeKey())));
        Files.writeString(Path.of("target", "phase6-startup-result.txt"),
                "ApplicationReadyEvent: predictions before ready=0; after ready=" + predictions.count()
                + "; digest notifications=" + notifications.count() + "; clock=2026-11-08T01:00 Asia/Bangkok\n");
    }
}
