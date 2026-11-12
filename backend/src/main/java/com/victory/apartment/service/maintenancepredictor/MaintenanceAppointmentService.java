package com.victory.apartment.service.maintenancepredictor;

import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class MaintenanceAppointmentService {
    private final RoomRepository rooms;
    private final MaintenanceTaskTypeRepository types;
    private final ScheduledReminderRepository reminders;
    private final MaintenancePredictorService predictor;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public MaintenanceAppointmentService(RoomRepository rooms, MaintenanceTaskTypeRepository types,
            ScheduledReminderRepository reminders, MaintenancePredictorService predictor, Clock clock,
            PlatformTransactionManager manager) {
        this.rooms = rooms;
        this.types = types;
        this.reminders = reminders;
        this.predictor = predictor;
        this.clock = clock.withZone(PredictionConfig.ZONE);
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Pair(String roomId, String category) {}
    public record Updated(MaintenancePrediction prediction, Room room, MaintenanceTaskType type, String appointmentDate) {}

    /** One bulk read; legacy/manual reminders participate by their existing fields. */
    public Map<Pair, String> appointmentDates() {
        return reminders.findAll().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()) && r.getNextDueDate() != null)
                .collect(Collectors.toMap(r -> new Pair(r.getRoomId(), r.getCategory()),
                        ScheduledReminder::getNextDueDate, (first, second) -> first.compareTo(second) <= 0 ? first : second));
    }

    public Updated put(String roomId, String taskTypeId, LocalDate date) {
        return atomic(() -> {
            var target = target(roomId, taskTypeId);
            if (date == null || date.isBefore(LocalDate.now(clock)))
                throw error(HttpStatus.BAD_REQUEST, "Appointment date is before today");
            var matches = reminders.findByRoomIdAndCategory(roomId, target.type().getCategoryKey());
            // Prefer the appointment shown by GET; otherwise reuse an inactive row.
            var reminder = matches.stream().sorted(Comparator
                    .comparing((ScheduledReminder r) -> !Boolean.TRUE.equals(r.getIsActive()))
                    .thenComparing(ScheduledReminder::getNextDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ScheduledReminder::getId)).findFirst().orElseGet(ScheduledReminder::new);
            if (reminder.getId() == null) {
                reminder.setId("rem-" + UUID.randomUUID().toString().substring(0, 8));
                reminder.setTitle(target.type().getNameEn() + " (Unit " + target.room().getRoomNumber() + ")");
                reminder.setCategory(target.type().getCategoryKey());
                reminder.setRoomId(roomId);
                reminder.setRoomNumber(target.room().getRoomNumber());
            }
            // Keep legacy duplicates for audit, but expose only this active appointment.
            for (var other : matches) if (!other.getId().equals(reminder.getId())) other.setIsActive(false);
            reminder.setFrequency("None");
            reminder.setNextDueDate(date.toString());
            reminder.setIsActive(true);
            reminder.setLastTriggered(null);
            reminders.saveAll(matches);
            reminders.saveAndFlush(reminder);
            return new Updated(predictor.setOverrideInCurrentTransaction(roomId, target.type(), date), target.room(), target.type(), date.toString());
        });
    }

    public Updated delete(String roomId, String taskTypeId) {
        return atomic(() -> {
            var target = target(roomId, taskTypeId);
            var matches = reminders.findByRoomIdAndCategory(roomId, target.type().getCategoryKey());
            matches.forEach(r -> r.setIsActive(false));
            reminders.saveAll(matches);
            return new Updated(predictor.setOverrideInCurrentTransaction(roomId, target.type(), null), target.room(), target.type(), null);
        });
    }

    public void completed(String roomId, String taskTypeId, LocalDate completedDate) {
        atomic(() -> {
            // Unknown/non-recurring history must never enter the prediction lifecycle.
            var type = types.findById(taskTypeId);
            if (type.isEmpty() || !Boolean.TRUE.equals(type.get().getIsRecurring())) return null;
            var target = target(roomId, taskTypeId);
            var prediction = predictor.setOverrideInCurrentTransaction(roomId, target.type(), null);
            var active = reminders.findByRoomIdAndCategory(roomId, target.type().getCategoryKey()).stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsActive())).toList();
            for (var reminder : active) {
                reminder.setLastTriggered(completedDate.toString());
                if (prediction.getPredictedDate() == null) reminder.setIsActive(false);
                else reminder.setNextDueDate(prediction.getPredictedDate().toString());
            }
            reminders.saveAll(active);
            return null;
        });
    }

    private record Target(Room room, MaintenanceTaskType type) {}
    private Target target(String roomId, String taskTypeId) {
        // Existing row lock serializes concurrent appointment creates even when
        // no reminder/prediction row exists yet. Shared across processes/instances.
        var room = rooms.findByIdForUpdate(roomId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Room not found"));
        var type = types.findById(taskTypeId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Task type not found"));
        if (!Boolean.TRUE.equals(type.getIsRecurring())) throw error(HttpStatus.BAD_REQUEST, "Task type is not recurring");
        return new Target(room, type);
    }

    private <T> T atomic(Supplier<T> work) {
        try { return transaction.execute(ignored -> work.get()); }
        catch (DataIntegrityViolationException exception) {
            if (!predictor.isPairConflict(exception)) throw exception;
            return transaction.execute(ignored -> work.get());
        }
    }

    private ResponseStatusException error(HttpStatus status, String reason) { return new ResponseStatusException(status, reason); }
}
