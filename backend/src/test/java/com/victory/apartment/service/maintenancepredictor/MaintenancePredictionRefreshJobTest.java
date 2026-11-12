package com.victory.apartment.service.maintenancepredictor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaintenancePredictionRefreshJobTest {
    private final MaintenancePredictorService predictor = mock(MaintenancePredictorService.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(MaintenancePredictorService.class, () -> predictor)
            .withUserConfiguration(MaintenancePredictionRefreshJob.class);
    @Test void testProfileRequiresExplicitOptInEvenWhenPropertiesEnabled() {
        runner.withPropertyValues("spring.profiles.active=test", "app.maintenance.prediction.refresh-on-startup=true")
                .run(context -> { assertThat(context).doesNotHaveBean(MaintenancePredictionRefreshJob.class); verifyNoInteractions(predictor); });
    }
    @Test void optedInStartupFailureLeavesApplicationContextAvailableAndAttemptsOnlyOnce() {
        when(predictor.refreshAll()).thenThrow(new IllegalStateException("database unavailable"));
        runner.withPropertyValues("spring.profiles.active=test,maintenance-prediction-jobs").run(context -> {
            var event = new ApplicationReadyEvent(new SpringApplication(MaintenancePredictionRefreshJob.class), new String[0], context.getSourceApplicationContext(), Duration.ZERO);
            assertDoesNotThrow(() -> { context.publishEvent(event); context.publishEvent(event); });
            assertThat(context).hasNotFailed(); verify(predictor, times(1)).refreshAll();
        });
    }
    @Test void propertiesCanDisableStartupAndDailyIndependently() {
        runner.withPropertyValues("spring.profiles.active=test,maintenance-prediction-jobs", "app.maintenance.prediction.refresh-on-startup=false",
                "app.maintenance.prediction.daily-refresh-enabled=false").run(context -> {
            var job = context.getBean(MaintenancePredictionRefreshJob.class); job.onReady(); job.daily(); verifyNoInteractions(predictor);
        });
    }
    @Test void dailyRefreshRunsWhenEnabledAndContainsFailures() {
        when(predictor.refreshAll()).thenThrow(new IllegalStateException("unavailable"));
        runner.withPropertyValues("spring.profiles.active=test,maintenance-prediction-jobs", "app.maintenance.prediction.refresh-on-startup=false")
                .run(context -> { assertDoesNotThrow(context.getBean(MaintenancePredictionRefreshJob.class)::daily); verify(predictor).refreshAll(); });
    }
    @Test void scheduledTimeDefaultsToSixAmBangkok() throws Exception {
        var annotation = MaintenancePredictionRefreshJob.class.getMethod("daily").getAnnotation(Scheduled.class);
        assertEquals("Asia/Bangkok", annotation.zone());
        assertEquals("${app.maintenance.prediction.daily-refresh-cron:0 0 6 * * *}", annotation.cron());
    }
}
