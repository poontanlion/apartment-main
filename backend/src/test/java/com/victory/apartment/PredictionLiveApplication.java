package com.victory.apartment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.PostgreSQLContainer;
import java.time.*;

/** Test runtime only. A fresh Docker database is mandatory; never uses DB_URL. */
public final class PredictionLiveApplication {
    public static void main(String[] args) {
        var database = new PostgreSQLContainer<>("postgres:16-alpine");
        database.start();
        Runtime.getRuntime().addShutdownHook(new Thread(database::stop));
        System.setProperty("spring.datasource.url", database.getJdbcUrl());
        System.setProperty("spring.datasource.username", database.getUsername());
        System.setProperty("spring.datasource.password", database.getPassword());
        System.setProperty("app.admin.email", "smoke-admin@example.com");
        System.setProperty("app.admin.password", "prediction-smoke-password");
        System.setProperty("app.seed.prediction-demo", "true");
        System.setProperty("app.maintenance.prediction.daily-refresh-enabled", "false");
        var application = new SpringApplication(Application.class, TimeConfig.class);
        application.setAdditionalProfiles("prediction-live");
        try { application.run(args); }
        catch (RuntimeException exception) { database.stop(); throw exception; }
    }
    @TestConfiguration
    @Profile("prediction-live")
    static class TimeConfig {
        @Bean @Primary Clock liveClock() {
            return Clock.fixed(Instant.parse("2026-11-07T18:00:00Z"), ZoneOffset.UTC);
        }
    }
}
