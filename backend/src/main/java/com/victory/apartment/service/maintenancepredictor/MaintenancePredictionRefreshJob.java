package com.victory.apartment.service.maintenancepredictor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Profile("!test | maintenance-prediction-jobs")
public class MaintenancePredictionRefreshJob {
    private static final Logger logger = LoggerFactory.getLogger(MaintenancePredictionRefreshJob.class);
    private final MaintenancePredictorService predictor;
    private final boolean startupEnabled;
    private final boolean dailyEnabled;
    private final AtomicBoolean startupAttempted = new AtomicBoolean();

    public MaintenancePredictionRefreshJob(MaintenancePredictorService predictor,
            @Value("${app.maintenance.prediction.refresh-on-startup:true}") boolean startupEnabled,
            @Value("${app.maintenance.prediction.daily-refresh-enabled:true}") boolean dailyEnabled) {
        this.predictor = predictor;
        this.startupEnabled = startupEnabled;
        this.dailyEnabled = dailyEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (startupEnabled && startupAttempted.compareAndSet(false, true)) refreshSafely("startup");
    }

    @Scheduled(cron = "${app.maintenance.prediction.daily-refresh-cron:0 0 6 * * *}", zone = "Asia/Bangkok")
    public void daily() {
        if (dailyEnabled) refreshSafely("daily");
    }

    private void refreshSafely(String source) {
        try {
            var result = predictor.refreshAll();
            logger.info("Maintenance prediction {} refresh completed: {} successful pairs, {} failed pairs",
                    source, result.success(), result.failure());
        } catch (RuntimeException exception) {
            logger.error("Maintenance prediction {} refresh failed; application remains available", source, exception);
        }
    }
}
