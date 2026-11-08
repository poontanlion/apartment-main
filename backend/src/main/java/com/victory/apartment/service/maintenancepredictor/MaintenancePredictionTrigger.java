package com.victory.apartment.service.maintenancepredictor;

import com.victory.apartment.model.MaintenanceLog;
import com.victory.apartment.model.MaintenanceTask;
import com.victory.apartment.repository.MaintenanceTaskTypeRepository;
import com.victory.apartment.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
public class MaintenancePredictionTrigger {
    private static final Logger logger = LoggerFactory.getLogger(MaintenancePredictionTrigger.class);
    private final MaintenanceTaskTypeRepository types;
    private final RoomRepository rooms;
    private final MaintenancePredictorService predictor;
    private final Clock clock;
    private final MaintenanceAppointmentService appointments;

    public MaintenancePredictionTrigger(MaintenanceTaskTypeRepository types, RoomRepository rooms,
            MaintenancePredictorService predictor, Clock clock, MaintenanceAppointmentService appointments) {
        this.types = types;
        this.rooms = rooms;
        this.predictor = predictor;
        this.clock = clock.withZone(PredictionConfig.ZONE);
        this.appointments = appointments;
    }

    /** The single policy for task/manual history dates, also used by legacy GET sync. */
    public void prepare(MaintenanceLog log, MaintenanceTask task, boolean newlyCompleted) {
        log.setTaskTypeId(types.findAll().stream()
                .filter(t -> log.getCategory() != null && log.getCategory().equals(t.getCategoryKey()))
                .map(t -> t.getId()).findFirst().orElse(null));
        if (log.getRoomId() == null || !rooms.existsById(log.getRoomId())) {
            log.setCompletedDate(null);
        } else if (task != null) {
            if (!"Completed".equals(task.getStatus())) log.setCompletedDate(null);
            else if (newlyCompleted) log.setCompletedDate(LocalDate.now(clock));
            else if (log.getCompletedDate() == null && task.getCompletedAt() != null)
                log.setCompletedDate(task.getCompletedAt().toLocalDate());
        } else if (log.getTaskNo() == null || log.getTaskNo().isBlank()) {
            log.setCompletedDate(parseManualDate(log.getDate()));
        } else {
            log.setCompletedDate(null);
        }
    }

    private LocalDate parseManualDate(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException exception) { return null; }
    }

    public void recalculateSafely(String roomId, String taskTypeId) {
        if (roomId == null || taskTypeId == null) return;
        try {
            predictor.recalculate(roomId, taskTypeId);
        } catch (RuntimeException exception) {
            logger.error("Prediction trigger failed for room {} task type {}; maintenance write retained",
                    roomId, taskTypeId, exception);
        }
    }

    public void completeSafely(MaintenanceLog log) {
        if (log.getRoomId() == null || log.getTaskTypeId() == null || log.getCompletedDate() == null) return;
        try {
            appointments.completed(log.getRoomId(), log.getTaskTypeId(), log.getCompletedDate());
        } catch (RuntimeException exception) {
            logger.error("Prediction completion failed for room {} task type {}; maintenance write retained",
                    log.getRoomId(), log.getTaskTypeId(), exception);
        }
    }
}
