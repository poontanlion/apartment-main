package com.victory.apartment.service.maintenancepredictor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import static com.victory.apartment.service.maintenancepredictor.PredictionConfig.*;

public enum PredictionStatus {
    OVERDUE, DUE_SOON, WITHIN_30, ON_TRACK, NO_DATA;

    public static PredictionStatus from(LocalDate predictedDate, LocalDate today) {
        Objects.requireNonNull(today, "today");
        if (predictedDate == null) return NO_DATA;
        long remaining = ChronoUnit.DAYS.between(today, predictedDate);
        if (remaining < 0) return OVERDUE;
        if (remaining <= DUE_SOON_DAYS) return DUE_SOON;
        if (remaining <= WITHIN_DAYS) return WITHIN_30;
        return ON_TRACK;
    }
}
