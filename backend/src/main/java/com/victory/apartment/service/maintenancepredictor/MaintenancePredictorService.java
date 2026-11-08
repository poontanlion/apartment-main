package com.victory.apartment.service.maintenancepredictor;

import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MaintenancePredictorService {
    private static final Logger logger = LoggerFactory.getLogger(MaintenancePredictorService.class);
    private final RoomRepository rooms;
    private final MaintenanceTaskTypeRepository types;
    private final MaintenanceLogRepository logs;
    private final LeaseRepository leases;
    private final MaintenancePredictionRepository predictions;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public MaintenancePredictorService(RoomRepository rooms, MaintenanceTaskTypeRepository types,
            MaintenanceLogRepository logs, LeaseRepository leases,
            MaintenancePredictionRepository predictions, Clock clock, PlatformTransactionManager transactionManager) {
        this.rooms = rooms;
        this.types = types;
        this.logs = logs;
        this.leases = leases;
        this.predictions = predictions;
        this.clock = clock.withZone(PredictionConfig.ZONE);
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Missing rooms and non-recurring types are skipped without persistence. */
    public Optional<MaintenancePrediction> recalculate(String roomId, String taskTypeId) {
        return inPairTransaction(roomId, taskTypeId, false, null);
    }

    public Optional<MaintenancePrediction> setOverride(String roomId, String taskTypeId, LocalDate overrideDate) {
        return inPairTransaction(roomId, taskTypeId, true, overrideDate);
    }

    private Optional<MaintenancePrediction> inPairTransaction(String roomId, String taskTypeId,
            boolean changeOverride, LocalDate overrideDate) {
        try {
            return calculateInTransaction(roomId, taskTypeId, changeOverride, overrideDate);
        } catch (DataIntegrityViolationException exception) {
            // PostgreSQL aborts a transaction after a constraint error. Retry only
            // this pair's unique conflict, once, in a fresh transaction/context.
            if (!isPairConflict(exception)) throw exception;
            return calculateInTransaction(roomId, taskTypeId, changeOverride, overrideDate);
        }
    }

    private Optional<MaintenancePrediction> calculateInTransaction(String roomId, String taskTypeId,
            boolean changeOverride, LocalDate overrideDate) {
        return transaction.execute(ignored -> {
            var type = types.findById(taskTypeId);
            if (type.isEmpty() || !Boolean.TRUE.equals(type.get().getIsRecurring()) || !rooms.existsById(roomId))
                return Optional.empty();
            return Optional.of(calculateAndSave(roomId, type.get(), LocalDate.now(clock), changeOverride, overrideDate));
        });
    }

    private boolean isPairConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint
                    && "23505".equals(constraint.getSQLState())
                    && "uk_maintenance_predictions_room_task".equals(constraint.getConstraintName())) return true;
        }
        return false;
    }

    public record RefreshResult(int success, int failure) {}

    public RefreshResult refreshAll() {
        int success = 0;
        int failure = 0;
        var recurringTypes = types.findAll().stream().filter(t -> Boolean.TRUE.equals(t.getIsRecurring())).toList();
        for (var room : rooms.findAll()) {
            for (var type : recurringTypes) {
                try {
                    if (recalculate(room.getId(), type.getId()).isPresent()) success++;
                    else failure++;
                } catch (RuntimeException exception) {
                    failure++;
                    logger.error("Prediction refresh failed for room {} task type {}", room.getId(), type.getId(), exception);
                }
            }
        }
        return new RefreshResult(success, failure);
    }

    private MaintenancePrediction calculateAndSave(String roomId, MaintenanceTaskType type, LocalDate today,
            boolean changeOverride, LocalDate overrideDate) {
        // Reuse the existing repository; its legacy text-date order is irrelevant to
        // the calculator, which sorts completedDate values without parsing date.
        var dates = logs.findByRoomIdOrderByDateDesc(roomId).stream()
                .filter(l -> type.getId().equals(l.getTaskTypeId()))
                .map(MaintenanceLog::getCompletedDate).filter(Objects::nonNull).toList();
        var leaseStart = leases.findByRoomIdAndStatus(roomId, "Active").stream()
                .map(Lease::getCheckInDate).filter(Objects::nonNull).filter(d -> !d.isAfter(today))
                .max(Comparator.naturalOrder()).orElse(null);
        var row = predictions.findByRoomIdAndTaskTypeId(roomId, type.getId()).orElseGet(() -> {
            var created = new MaintenancePrediction();
            created.setId("pred-" + UUID.randomUUID().toString().substring(0, 8));
            created.setRoomId(roomId);
            created.setTaskTypeId(type.getId());
            return created;
        });
        if (changeOverride) row.setOverrideDate(overrideDate);
        row.setPredictedDate(PredictionCalculator.calculate(dates, type.getDefaultIntervalDays(),
                leaseStart, row.getOverrideDate(), today, "AIRCON".equals(type.getCode())));
        row.setComputedAt(LocalDateTime.now(clock));
        return predictions.saveAndFlush(row);
    }
}
