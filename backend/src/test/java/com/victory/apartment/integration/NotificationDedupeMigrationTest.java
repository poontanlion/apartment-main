package com.victory.apartment.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class NotificationDedupeMigrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    private Flyway migrate(String schema, String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).target(target).load();
    }
    private Connection connection(String schema) throws SQLException {
        var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setSchema(schema); return connection;
    }
    @Test void emptyDatabaseMigratesThroughV5AndEnforcesPartialRecipientIndex() throws Exception {
        migrate("empty_v5", "8").migrate();
        try (var connection = connection("empty_v5"); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO app_notifications(id,user_id,dedupe_key) VALUES ('n1','a','daily'), ('n2','b','daily')");
            var duplicate = assertThrows(SQLException.class, () -> statement.execute("INSERT INTO app_notifications(id,user_id,dedupe_key) VALUES ('n3','a','daily')"));
            assertEquals("23505", duplicate.getSQLState());
            statement.execute("INSERT INTO app_notifications(id,user_id) VALUES ('legacy1','a'), ('legacy2','a'), ('legacy3','a')");
            try (var result = statement.executeQuery("SELECT count(*) FROM app_notifications WHERE dedupe_key IS NULL")) {
                result.next(); assertEquals(3, result.getLong(1));
            }
        }
    }
    @Test void upgradingV4PreservesExistingNotificationsAndNullKeysDoNotConflict() throws Exception {
        migrate("legacy_v5", "7").migrate();
        try (var connection = connection("legacy_v5"); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO app_notifications(id,user_id,title,type) VALUES ('old1','admin','Old notification','warning'), ('old2','admin','Old notification','warning')");
        }
        migrate("legacy_v5", "8").migrate();
        try (var connection = connection("legacy_v5"); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT count(*) FROM app_notifications WHERE title='Old notification' AND dedupe_key IS NULL")) {
                result.next(); assertEquals(2, result.getLong(1));
            }
            statement.execute("INSERT INTO app_notifications(id,user_id,title) VALUES ('new-legacy','admin','Still supported')");
        }
    }
}
