package com.victory.apartment.service.maintenancepredictor;

import com.victory.apartment.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DemoPredictionSeederGuardTest {
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final MaintenanceLogRepository logs = mock(MaintenanceLogRepository.class);
    private final MaintenancePredictorService predictor = mock(MaintenancePredictorService.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(DemoPredictionSeeder.class)
            .withBean(RoomRepository.class, () -> rooms).withBean(LeaseRepository.class, () -> mock(LeaseRepository.class))
            .withBean(MaintenanceTaskTypeRepository.class, () -> mock(MaintenanceTaskTypeRepository.class))
            .withBean(MaintenanceLogRepository.class, () -> logs).withBean(MaintenancePredictionRepository.class, () -> mock(MaintenancePredictionRepository.class))
            .withBean(ScheduledReminderRepository.class, () -> mock(ScheduledReminderRepository.class))
            .withBean(MaintenancePredictorService.class, () -> predictor).withBean(Clock.class, Clock::systemUTC)
            .withBean(PlatformTransactionManager.class, () -> {
                var manager = mock(PlatformTransactionManager.class);
                when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus()); return manager;
            });
    @Test void disabledByDefault() { runner.run(context -> assertThat(context).doesNotHaveBean(DemoPredictionSeeder.class)); }
    @Test void prodAlwaysBlocksSeederEvenWithOptIn() {
        runner.withPropertyValues("spring.profiles.active=prod,prediction-demo", "app.seed.prediction-demo=true")
                .run(context -> assertThat(context).doesNotHaveBean(DemoPredictionSeeder.class));
    }
    @Test void testProfileRequiresExplicitOptIn() {
        runner.withPropertyValues("spring.profiles.active=test", "app.seed.prediction-demo=true")
                .run(context -> assertThat(context).doesNotHaveBean(DemoPredictionSeeder.class));
    }
    @Test void optedInFailureDoesNotEscapeTheRunner() {
        when(rooms.findAllByOrderByRoomNumberAsc()).thenThrow(new IllegalStateException("unavailable"));
        runner.withPropertyValues("spring.profiles.active=test,prediction-demo", "app.seed.prediction-demo=true").run(context -> {
            assertDoesNotThrow(() -> context.getBean(DemoPredictionSeeder.class).run(null));
            verify(logs, never()).deleteAll(anyList()); verifyNoInteractions(predictor);
        });
    }
    @Test void missingRoomsAndTypesAreSkippedWithoutManufacturingData() {
        runner.withPropertyValues("spring.profiles.active=test,prediction-demo", "app.seed.prediction-demo=true").run(context -> {
            assertFalse(context.getBean(DemoPredictionSeeder.class).seed(false).failed());
            verify(rooms, never()).save(any()); verify(logs, never()).save(any()); verify(predictor).refreshAll();
        });
    }
}
