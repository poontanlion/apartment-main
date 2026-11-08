package com.victory.apartment.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class MaintenancePredictionMigrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private Flyway migration(String schema, String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).target(target).load();
    }

    @Test
    void migratesEmptyDatabaseAndSeedsFiveTypes() throws Exception {
        migration("empty_database", "7").migrate();
        try (var c = connection("empty_database")) {
            assertEquals(5, number(c, "SELECT count(*) FROM maintenance_task_types"));
            assertEquals(4, number(c, "SELECT count(*) FROM maintenance_task_types WHERE is_recurring"));
            assertEquals(1, number(c, "SELECT count(*) FROM maintenance_task_types WHERE code='GENERAL' AND default_interval_days IS NULL AND NOT is_recurring"));
            assertEquals(1, number(c, "SELECT count(*) FROM maintenance_task_types WHERE code='AIRCON' AND category_key='Air-con servicing' AND default_interval_days=180"));
        }
    }

    @Test
    void backfillsConservativelyAndCanBeRepeatedWithoutChangingLegacyValues() throws Exception {
        migration("legacy_database", "6").migrate();
        try (var c = connection("legacy_database"); var s = c.createStatement()) {
            s.execute("INSERT INTO rooms(id,room_number,floor) VALUES ('rm-A101','A101',1)");
            s.execute("""
                INSERT INTO maintenance_tasks(id,task_no,status,completed_at) VALUES
                ('t1','done','Completed','2026-07-21 00:30:00'),
                ('t2','pending','Pending','2026-07-21 10:00:00'),
                ('t3','cancelled','Cancelled','2026-07-21 10:00:00'),
                ('t4','missing-time','Completed',NULL)
                """);
            s.execute("""
                INSERT INTO maintenance_logs(id,room_id,category,task_no,date) VALUES
                ('done','rm-A101','Air-con servicing','done','2020-01-01'),
                ('pending','rm-A101','Air-con servicing','pending','2026-07-21'),
                ('cancelled','rm-A101','Air-con servicing','cancelled','2026-07-21'),
                ('missing-time','rm-A101','Air-con servicing','missing-time','2026-07-21'),
                ('orphan','rm-101','Air-con servicing',NULL,'2026-07-21'),
                ('manual','rm-A101','Light bulb replacement',NULL,'2026-07-21'),
                ('invalid','rm-A101','Electrical',NULL,'not-a-date'),
                ('impossible','rm-A101','Plumbing',NULL,'2026-02-30'),
                ('unknown','rm-A101','air-con servicing',NULL,'2026-07-21'),
                ('unmatched','rm-A101','General Repair','no-such-task','2026-07-21'),
                ('blank','rm-A101','Plumbing',' ','2026-07-21'),
                ('leap','rm-A101','Electrical',NULL,'2024-02-29')
                """);
        }
        migration("legacy_database", "7").migrate();
        try (var c = connection("legacy_database"); var s = c.createStatement()) {
            assertEquals(12, number(c, "SELECT count(*) FROM maintenance_logs"));
            assertEquals(11, number(c, "SELECT count(*) FROM maintenance_logs WHERE task_type_id IS NOT NULL"));
            assertEquals(5, number(c, "SELECT count(*) FROM maintenance_logs WHERE completed_date IS NOT NULL"));
            assertEquals(7, number(c, "SELECT count(*) FROM maintenance_logs WHERE completed_date IS NULL"));
            assertEquals(1, number(c, "SELECT count(*) FROM maintenance_logs WHERE id='done' AND completed_date=DATE '2026-07-21' AND date='2020-01-01'"));
            assertEquals(1, number(c, "SELECT count(*) FROM maintenance_logs WHERE id='unknown' AND task_type_id IS NULL"));
            assertEquals(7, number(c, "SELECT count(*) FROM maintenance_logs WHERE id IN ('pending','cancelled','missing-time','orphan','invalid','impossible','unmatched') AND completed_date IS NULL"));
            // SQL itself is repeatable, including when the connection timezone differs.
            s.execute("SET TIME ZONE 'America/Los_Angeles'");
            try (var resource = getClass().getResourceAsStream("/db/migration/V7__maintenance_predictions.sql")) {
                assertNotNull(resource);
                s.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertEquals(5, number(c, "SELECT count(*) FROM maintenance_task_types"));
            assertEquals(12, number(c, "SELECT count(*) FROM maintenance_logs"));
            assertEquals(5, number(c, "SELECT count(*) FROM maintenance_logs WHERE completed_date IS NOT NULL"));
            assertThrows(SQLException.class, () -> s.execute("INSERT INTO maintenance_predictions(id,room_id,task_type_id) VALUES ('bad','missing','mtt-aircon')"));
            s.execute("INSERT INTO maintenance_predictions(id,room_id,task_type_id) VALUES ('p1','rm-A101','mtt-aircon')");
            assertThrows(SQLException.class, () -> s.execute("INSERT INTO maintenance_predictions(id,room_id,task_type_id) VALUES ('p2','rm-A101','mtt-aircon')"));
            System.out.println("V4 fixture: total=12, mapped=11, completed=5, skipped completion=7; unknown category=1");
        }
    }

    private Connection connection(String schema) throws SQLException {
        var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        c.setSchema(schema);
        return c;
    }

    private long number(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery(sql)) { r.next(); return r.getLong(1); }
    }
}
