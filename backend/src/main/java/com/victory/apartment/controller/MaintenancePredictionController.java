package com.victory.apartment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
import com.victory.apartment.service.maintenancepredictor.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/maintenance/predictions")
public class MaintenancePredictionController {
    private final MaintenancePredictionRepository predictions;
    private final RoomRepository rooms;
    private final MaintenanceTaskTypeRepository types;
    private final AppUserRepository users;
    private final MaintenancePredictorService predictor;
    private final Clock clock;
    private final String adminEmail;

    public MaintenancePredictionController(MaintenancePredictionRepository predictions, RoomRepository rooms,
            MaintenanceTaskTypeRepository types, AppUserRepository users, MaintenancePredictorService predictor,
            Clock clock, @Value("${app.admin.email:${ADMIN_EMAIL:admin@victoryapartment.com}}") String adminEmail) {
        this.predictions = predictions;
        this.rooms = rooms;
        this.types = types;
        this.users = users;
        this.predictor = predictor;
        this.clock = clock.withZone(PredictionConfig.ZONE);
        this.adminEmail = adminEmail;
    }

    public record PredictionRow(String roomId, String roomNumber, String buildingId, String buildingName,
            String taskTypeId, String taskTypeNameTh, String taskTypeNameEn, LocalDate predictedDate,
            Long daysRemaining, String status, boolean hasAppointment) {}
    public record Summary(long overdue, long dueSoon, long within30, long total) {}
    public record PredictionList(List<PredictionRow> predictions, Summary summary) {}

    @GetMapping
    public PredictionList list(HttpServletRequest request,
            @RequestParam(required = false) String roomId,
            @RequestParam(required = false) String taskTypeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String buildingId) {
        requireAdmin(request);
        Set<String> statuses = parseStatuses(status);
        LocalDate today = LocalDate.now(clock);
        // Three bulk reads independent of row count; no recalculation or writes.
        var roomMap = rooms.findAll().stream().collect(Collectors.toMap(Room::getId, Function.identity()));
        var typeMap = types.findAll().stream().filter(t -> Boolean.TRUE.equals(t.getIsRecurring()))
                .collect(Collectors.toMap(MaintenanceTaskType::getId, Function.identity()));
        String search = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        var filtered = predictions.findAll().stream()
                .filter(p -> roomMap.containsKey(p.getRoomId()) && typeMap.containsKey(p.getTaskTypeId()))
                .map(p -> row(p, roomMap.get(p.getRoomId()), typeMap.get(p.getTaskTypeId()), today))
                .filter(p -> matches(roomId, p.roomId()) && matches(taskTypeId, p.taskTypeId())
                        && matches(buildingId, p.buildingId()))
                .filter(p -> search.isEmpty() || contains(p.roomNumber(), search)
                        || contains(p.taskTypeNameTh(), search) || contains(p.taskTypeNameEn(), search)
                        || contains(p.buildingName(), search))
                .sorted(Comparator.comparing(PredictionRow::predictedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PredictionRow::roomId).thenComparing(PredictionRow::taskTypeId))
                .toList();
        var summary = new Summary(count(filtered, "overdue"), count(filtered, "due_soon"),
                count(filtered, "due_soon") + count(filtered, "within_30"), filtered.size());
        return new PredictionList(filtered.stream().filter(p -> statuses.isEmpty() || statuses.contains(p.status())).toList(), summary);
    }

    @PostMapping("/refresh")
    public MaintenancePredictorService.RefreshResult refresh(HttpServletRequest request) {
        requireAdmin(request);
        return predictor.refreshAll();
    }

    @PatchMapping("/{roomId}/{taskTypeId}")
    public PredictionRow override(HttpServletRequest request, @PathVariable String roomId,
            @PathVariable String taskTypeId, @RequestBody JsonNode body) {
        requireAdmin(request);
        var room = rooms.findById(roomId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Room not found"));
        var type = types.findById(taskTypeId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Task type not found"));
        if (!Boolean.TRUE.equals(type.getIsRecurring())) throw error(HttpStatus.BAD_REQUEST, "Task type is not recurring");
        if (!body.isObject() || !body.has("overrideDate")) throw error(HttpStatus.BAD_REQUEST, "overrideDate is required");
        LocalDate date = null;
        JsonNode value = body.get("overrideDate");
        if (!value.isNull()) {
            if (!value.isTextual() || !value.textValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
                throw error(HttpStatus.BAD_REQUEST, "overrideDate must be YYYY-MM-DD or null");
            try {
                date = LocalDate.parse(value.textValue());
            } catch (DateTimeParseException exception) {
                throw error(HttpStatus.BAD_REQUEST, "Invalid overrideDate");
            }
            if (date.isBefore(LocalDate.now(clock))) throw error(HttpStatus.BAD_REQUEST, "overrideDate is before today");
        }
        var updated = predictor.setOverride(roomId, taskTypeId, date)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Prediction target no longer exists"));
        return row(updated, room, type, LocalDate.now(clock));
    }

    private void requireAdmin(HttpServletRequest request) {
        var session = request.getSession(false);
        Object identity = session == null ? null : session.getAttribute("userEmail");
        if (!(identity instanceof String email) || email.isBlank()) throw error(HttpStatus.UNAUTHORIZED, "Sign in required");
        var user = users.findByEmail(email);
        // AuthController supports a configured admin even before its DB row exists.
        boolean admin = user.map(u -> "admin".equalsIgnoreCase(u.getRole()))
                .orElseGet(() -> adminEmail.equalsIgnoreCase(email));
        if (!admin) throw error(HttpStatus.FORBIDDEN, "Admin access required");
    }

    private Set<String> parseStatuses(String value) {
        if (value == null) return Set.of();
        Set<String> result = new HashSet<>();
        for (String token : value.split(",", -1)) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            try {
                PredictionStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw error(HttpStatus.BAD_REQUEST, "Unknown prediction status");
            }
            result.add(normalized);
        }
        return result;
    }

    private PredictionRow row(MaintenancePrediction prediction, Room room, MaintenanceTaskType type, LocalDate today) {
        var date = prediction.getPredictedDate();
        return new PredictionRow(room.getId(), room.getRoomNumber(), room.getBuildingId(), room.getBuildingName(),
                type.getId(), type.getNameTh(), type.getNameEn(), date,
                date == null ? null : ChronoUnit.DAYS.between(today, date),
                PredictionStatus.from(date, today).name().toLowerCase(Locale.ROOT), false);
    }

    private long count(List<PredictionRow> rows, String status) {
        return rows.stream().filter(p -> p.status().equals(status)).count();
    }

    private boolean matches(String filter, String value) { return filter == null || filter.isBlank() || filter.equals(value); }
    private boolean contains(String value, String search) { return value != null && value.toLowerCase(Locale.ROOT).contains(search); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
