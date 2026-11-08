package com.victory.apartment.service.maintenancepredictor;

import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;

/** Opt-in demo data only; never manufactures rooms, leases, tenants or overrides. */
@Component
@Order(100)
@Profile("!prod & (!test | prediction-demo)")
@ConditionalOnProperty(name = "app.seed.prediction-demo", havingValue = "true")
public class DemoPredictionSeeder implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(DemoPredictionSeeder.class);
    private static final String ID_PREFIX = "log-demo-pred-";
    private static final String DESCRIPTION_PREFIX = "[DEMO]";
    private final RoomRepository rooms;
    private final LeaseRepository leases;
    private final MaintenanceTaskTypeRepository types;
    private final MaintenanceLogRepository logs;
    private final MaintenancePredictionRepository predictions;
    private final ScheduledReminderRepository reminders;
    private final MaintenancePredictorService predictor;
    private final Clock clock;
    private final boolean reset;
    private final TransactionTemplate transaction;

    public DemoPredictionSeeder(RoomRepository rooms, LeaseRepository leases, MaintenanceTaskTypeRepository types,
            MaintenanceLogRepository logs, MaintenancePredictionRepository predictions, ScheduledReminderRepository reminders,
            MaintenancePredictorService predictor, Clock clock, PlatformTransactionManager manager,
            @Value("${app.seed.prediction-demo.reset:false}") boolean reset) {
        this.rooms = rooms; this.leases = leases; this.types = types; this.logs = logs;
        this.predictions = predictions; this.reminders = reminders; this.predictor = predictor;
        this.clock = clock.withZone(PredictionConfig.ZONE); this.reset = reset;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record ScenarioResult(String scenario, String roomId, String taskTypeId,
            PredictionStatus status, LocalDate predictedDate, int historyCount) {}
    public record SeedResult(List<ScenarioResult> scenarios, long demoLogs, boolean failed) {}
    private record HistoryCase(String name, String code, int[] intervalPercentages, PredictionStatus target, int preferredDays) {}
    private static final List<HistoryCase> CASES = List.of(
            new HistoryCase("aircon-six", "AIRCON", new int[]{95, 103, 97, 106, 100}, PredictionStatus.OVERDUE, -3),
            new HistoryCase("light-one", "LIGHT", new int[]{}, PredictionStatus.DUE_SOON, 3),
            new HistoryCase("electrical-two", "ELECTRICAL", new int[]{100}, PredictionStatus.WITHIN_30, 14),
            new HistoryCase("plumbing-volatile", "PLUMBING", new int[]{15, 180}, PredictionStatus.WITHIN_30, 23),
            new HistoryCase("aircon-one", "AIRCON", new int[]{}, PredictionStatus.ON_TRACK, 45),
            new HistoryCase("light-two", "LIGHT", new int[]{92}, PredictionStatus.ON_TRACK, 65));

    @Override public void run(ApplicationArguments arguments) { seed(reset); }

    public SeedResult seed(boolean resetDemo) {
        try {
            var result = transaction.execute(ignored -> writeDemo(resetDemo));
            predictor.refreshAll(); // Also works when startup refresh is explicitly disabled.
            logger.info("Prediction demo ready: {} scenarios, {} demo logs", result.scenarios().size(), result.demoLogs());
            result.scenarios().forEach(row -> logger.info("Prediction demo {}: room {}, type {}, status {}, date {}",
                    row.scenario(), row.roomId(), row.taskTypeId(), row.status(), row.predictedDate()));
            return result;
        } catch (RuntimeException exception) {
            logger.error("Prediction demo seed failed; application remains available", exception);
            return new SeedResult(List.of(), 0, true);
        }
    }

    private SeedResult writeDemo(boolean resetDemo) {
        if (resetDemo) {
            logs.deleteAll(logs.findAll().stream().filter(this::isDemo).toList());
            logs.flush();
        }
        var today = LocalDate.now(clock);
        var allRooms = rooms.findAllByOrderByRoomNumberAsc();
        var allLeases = leases.findAll();
        var allLogs = logs.findAll();
        var allPredictions = predictions.findAll();
        var allReminders = reminders.findAll();
        var recurring = types.findAll().stream().filter(t -> Boolean.TRUE.equals(t.getIsRecurring())
                && t.getDefaultIntervalDays() != null && t.getDefaultIntervalDays() > 0).toList();
        Set<String> usedRooms = new HashSet<>();
        List<ScenarioResult> results = new ArrayList<>();

        // Reserve genuinely history-free rooms before assigning synthetic history.
        for (boolean active : List.of(true, false)) {
            String name = active ? "no-history-active-lease" : "no-history-no-lease";
            String code = active ? "LIGHT" : "ELECTRICAL";
            var type = recurring.stream().filter(t -> code.equals(t.getCode())).findFirst().orElse(null);
            if (type == null) { skip(name, "task type unavailable"); continue; }
            var room = allRooms.stream().filter(r -> !usedRooms.contains(r.getId()))
                    .filter(r -> allLogs.stream().noneMatch(l -> r.getId().equals(l.getRoomId()) && l.getCompletedDate() != null))
                    .filter(r -> safePair(r, type, allLogs, allPredictions, allReminders))
                    .filter(r -> active ? leaseStart(r.getId(), allLeases, today) != null
                            : allLeases.stream().noneMatch(l -> r.getId().equals(l.getRoomId()) && "Active".equals(l.getStatus())))
                    .findFirst().orElse(null);
            if (room == null) { skip(name, "no real history-free room with matching lease state"); continue; }
            usedRooms.add(room.getId());
            var date = PredictionCalculator.calculate(List.of(), type.getDefaultIntervalDays(),
                    leaseStart(room.getId(), allLeases, today), null, today, "AIRCON".equals(type.getCode()));
            results.add(new ScenarioResult(name, room.getId(), type.getId(), PredictionStatus.from(date, today), date, 0));
        }

        for (var scenario : CASES) {
            var type = recurring.stream().filter(t -> scenario.code().equals(t.getCode())).findFirst().orElse(null);
            if (type == null) { skip(scenario.name(), "task type unavailable"); continue; }
            String prefix = ID_PREFIX + scenario.name() + "-";
            String priorRoom = allLogs.stream().filter(this::isDemo).filter(l -> l.getId().startsWith(prefix))
                    .map(MaintenanceLog::getRoomId).findFirst().orElse(null);
            Predicate<Room> eligible = r -> !usedRooms.contains(r.getId()) && safePair(r, type, allLogs, allPredictions, allReminders);
            var room = allRooms.stream().filter(eligible).filter(r -> r.getId().equals(priorRoom)).findFirst()
                    .orElseGet(() -> allRooms.stream().filter(eligible).findFirst().orElse(null));
            if (room == null) { skip(scenario.name(), "no eligible real room without non-demo history or appointment"); continue; }
            var dates = findHistory(scenario, type, today);
            if (dates.isEmpty()) { skip(scenario.name(), "calculator cannot reach target with current task settings"); continue; }
            if (allLogs.stream().anyMatch(l -> l.getId().startsWith(prefix) && !isDemo(l))) {
                skip(scenario.name(), "marker ID is occupied by non-demo data"); continue;
            }
            usedRooms.add(room.getId());
            for (int i = 0; i < dates.size(); i++) {
                var log = new MaintenanceLog(); log.setId(prefix + (i + 1));
                log.setDescription(DESCRIPTION_PREFIX + " maintenance prediction: " + scenario.name());
                log.setRoomId(room.getId()); log.setRoomNumber(room.getRoomNumber());
                log.setCategory(type.getCategoryKey()); log.setTaskTypeId(type.getId());
                log.setCompletedDate(dates.get(i)); log.setDate(dates.get(i).toString());
                log.setPerformedBy("Demo fixture"); log.setTotalCost(0.0); logs.save(log);
            }
            var date = PredictionCalculator.calculate(dates, type.getDefaultIntervalDays(), null, null, today, "AIRCON".equals(type.getCode()));
            results.add(new ScenarioResult(scenario.name(), room.getId(), type.getId(), PredictionStatus.from(date, today), date, dates.size()));
        }
        logs.flush();
        return new SeedResult(List.copyOf(results), logs.findAll().stream().filter(this::isDemo).count(), false);
    }

    private boolean safePair(Room room, MaintenanceTaskType type, List<MaintenanceLog> allLogs,
            List<MaintenancePrediction> allPredictions, List<ScheduledReminder> allReminders) {
        return allLogs.stream().noneMatch(l -> !isDemo(l) && room.getId().equals(l.getRoomId())
                && type.getId().equals(l.getTaskTypeId()) && l.getCompletedDate() != null)
                && allPredictions.stream().noneMatch(p -> room.getId().equals(p.getRoomId()) && type.getId().equals(p.getTaskTypeId()) && p.getOverrideDate() != null)
                && allReminders.stream().noneMatch(r -> room.getId().equals(r.getRoomId()) && type.getCategoryKey().equals(r.getCategory()) && Boolean.TRUE.equals(r.getIsActive()));
    }
    private LocalDate leaseStart(String roomId, List<Lease> rows, LocalDate today) {
        return rows.stream().filter(l -> roomId.equals(l.getRoomId()) && "Active".equals(l.getStatus()))
                .map(Lease::getCheckInDate).filter(Objects::nonNull).filter(d -> !d.isAfter(today)).max(Comparator.naturalOrder()).orElse(null);
    }
    private boolean isDemo(MaintenanceLog log) {
        return log.getId() != null && log.getId().startsWith(ID_PREFIX)
                && log.getDescription() != null && log.getDescription().startsWith(DESCRIPTION_PREFIX);
    }
    private void skip(String scenario, String reason) { logger.warn("Prediction demo skipped {}: {}", scenario, reason); }

    /** Search actual calculator outputs; seasonal discontinuities need not hit an exact day. */
    private List<LocalDate> findHistory(HistoryCase scenario, MaintenanceTaskType type, LocalDate today) {
        long horizon = Math.min(36525, Math.max(3660L, type.getDefaultIntervalDays() * 10L));
        List<LocalDate> best = List.of(); long distance = Long.MAX_VALUE;
        for (long offset = 0; offset <= horizon; offset++) {
            var dates = history(today.minusDays(offset), scenario.intervalPercentages(), type.getDefaultIntervalDays());
            var predicted = PredictionCalculator.calculate(dates, type.getDefaultIntervalDays(), null, null, today, "AIRCON".equals(type.getCode()));
            if (PredictionStatus.from(predicted, today) != scenario.target()) continue;
            long delta = Math.abs(ChronoUnit.DAYS.between(predicted, today.plusDays(scenario.preferredDays())));
            if (delta < distance) { best = dates; distance = delta; }
            if (delta == 0) break;
        }
        return best;
    }
    private List<LocalDate> history(LocalDate last, int[] percentages, int defaultDays) {
        List<LocalDate> dates = new ArrayList<>(); dates.add(last);
        for (int i = percentages.length - 1; i >= 0; i--) {
            last = last.minusDays(Math.max(1, Math.round(defaultDays * percentages[i] / 100.0))); dates.add(last);
        }
        dates.sort(Comparator.naturalOrder()); return dates;
    }
}
