package com.victory.apartment.service.maintenancepredictor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import static com.victory.apartment.service.maintenancepredictor.PredictionConfig.*;

/** Pure date calculation, independent of persistence and the system clock. */
public final class PredictionCalculator {
    private PredictionCalculator() {}

    public static LocalDate calculate(List<LocalDate> completedDates, Integer defaultIntervalDays,
            LocalDate leaseStartDate, LocalDate overrideDate, LocalDate today, boolean airConditioning) {
        Objects.requireNonNull(today, "today");
        // An explicit appointment always wins, including past appointments.
        if (overrideDate != null) return overrideDate;
        if (defaultIntervalDays == null || defaultIntervalDays <= 0)
            throw new IllegalArgumentException("Recurring task requires a positive default interval");
        var dates = completedDates.stream().filter(Objects::nonNull)
                .filter(d -> !d.isAfter(today)).distinct().sorted().toList();
        LocalDate lastCompleted = dates.isEmpty() ? null : dates.get(dates.size() - 1);
        LocalDate anchor = lastCompleted == null ? leaseStartDate : lastCompleted;
        if (anchor == null || (lastCompleted == null && anchor.isAfter(today))) return null;
        double interval = dates.size() < MIN_HISTORY_FOR_WEIGHTING
                ? defaultInterval(defaultIntervalDays) : weightedInterval(dates, defaultIntervalDays);
        interval = seasonalAdjust(anchor, interval, airConditioning);
        return ensureAfterLastCompletion(anchor.plusDays(Math.round(interval)), lastCompleted);
    }

    static double defaultInterval(int defaultDays) { return defaultDays; }

    static double weightedInterval(List<LocalDate> dates, int defaultDays) {
        double total = 0;
        int weights = 0;
        int weight = FIRST_WEIGHT;
        for (int i = Math.max(1, dates.size() - MAX_INTERVALS); i < dates.size(); i++) {
            total += weight * ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
            weights += weight;
            weight += WEIGHT_INCREMENT;
        }
        return HISTORY_SHARE * total / weights + DEFAULT_SHARE * defaultDays;
    }

    static double seasonalAdjust(LocalDate anchor, double interval, boolean airConditioning) {
        int month = anchor.plusDays(Math.round(interval)).getMonthValue();
        return airConditioning && month >= SEASON_START_MONTH && month <= SEASON_END_MONTH
                ? interval * (1 - SEASON_REDUCTION) : interval;
    }

    static LocalDate ensureAfterLastCompletion(LocalDate predicted, LocalDate lastCompleted) {
        return lastCompleted != null && predicted.isBefore(lastCompleted)
                ? lastCompleted.plusDays(MIN_DAYS_AFTER_COMPLETION) : predicted;
    }
}
