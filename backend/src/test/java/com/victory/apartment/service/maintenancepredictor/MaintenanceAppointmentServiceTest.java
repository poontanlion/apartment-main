package com.victory.apartment.service.maintenancepredictor;

import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MaintenanceAppointmentServiceTest {
    @Test void nullNextPredictionDeactivatesReminderWithoutClearingItsDate() {
        var rooms = mock(RoomRepository.class); var types = mock(MaintenanceTaskTypeRepository.class);
        var reminders = mock(ScheduledReminderRepository.class); var predictor = mock(MaintenancePredictorService.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var room = new Room(); room.setId("rm-A101");
        var type = new MaintenanceTaskType(); type.setId("mtt-light"); type.setIsRecurring(true); type.setCategoryKey("Light bulb replacement");
        var reminder = new ScheduledReminder(); reminder.setIsActive(true); reminder.setNextDueDate("2026-11-20");
        when(rooms.findByIdForUpdate(room.getId())).thenReturn(Optional.of(room));
        when(types.findById(type.getId())).thenReturn(Optional.of(type));
        when(predictor.setOverrideInCurrentTransaction(room.getId(), type, null)).thenReturn(new MaintenancePrediction());
        when(reminders.findByRoomIdAndCategory(room.getId(), type.getCategoryKey())).thenReturn(List.of(reminder));
        var service = new MaintenanceAppointmentService(rooms, types, reminders, predictor,
                Clock.fixed(Instant.parse("2026-11-07T18:00:00Z"), ZoneOffset.UTC), manager);
        service.completed(room.getId(), type.getId(), LocalDate.of(2026, 11, 8));
        assertFalse(reminder.getIsActive()); assertEquals("2026-11-20", reminder.getNextDueDate());
        assertEquals("2026-11-08", reminder.getLastTriggered()); verify(manager).commit(any());
    }
}
