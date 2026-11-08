package com.victory.apartment.service.maintenancepredictor;

import java.time.ZoneId;

/** Internal algorithm policy; never expose these values through prediction APIs. */
public final class PredictionConfig {
    public static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
    public static final int MIN_HISTORY_FOR_WEIGHTING = 3;
    public static final int MAX_INTERVALS = 5;
    public static final int FIRST_WEIGHT = 1;
    public static final int WEIGHT_INCREMENT = 1;
    public static final double HISTORY_SHARE = 0.7;
    public static final double DEFAULT_SHARE = 0.3;
    public static final int SEASON_START_MONTH = 3;
    public static final int SEASON_END_MONTH = 6;
    public static final double SEASON_REDUCTION = 0.1;
    public static final int MIN_DAYS_AFTER_COMPLETION = 1;
    public static final int DUE_SOON_DAYS = 7;
    public static final int WITHIN_DAYS = 30;

    private PredictionConfig() {}
}
