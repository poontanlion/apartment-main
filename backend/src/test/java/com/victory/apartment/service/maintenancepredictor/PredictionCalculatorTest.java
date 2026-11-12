package com.victory.apartment.service.maintenancepredictor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PredictionCalculatorTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T18:00:00Z"), PredictionConfig.ZONE);
    private final LocalDate today = LocalDate.now(clock);
    private LocalDate calculate(List<LocalDate> dates, int interval, LocalDate start, boolean aircon) {
        return PredictionCalculator.calculate(dates, interval, start, null, today, aircon);
    }
    private LocalDate date(String value) { return LocalDate.parse(value); }

    @Test void noHistoryUsesLeaseStart() {
        assertEquals(date("2026-01-01"), calculate(List.of(),365,date("2025-01-01"),false));
    }
    @Test void noHistoryAndNoLeaseHasNoPrediction() {
        assertNull(calculate(List.of(),365,null,false));
    }
    @Test void futureLeaseIsNotAnAnchor() {
        assertNull(calculate(List.of(),365,today.plusDays(1),false));
    }
    @Test void oneCompletionUsesDefault() {
        assertEquals(date("2027-01-01"), calculate(List.of(date("2026-01-01")),365,null,false));
    }
    @Test void twoCompletionsStillUseDefault() {
        assertEquals(date("2027-01-11"), calculate(List.of(date("2026-01-01"),date("2026-01-11")),365,null,false));
    }
    @Test void threeCompletionsWeightLatestIntervalMoreAndRound() {
        // Intervals 10 and 20: weighted mean 50/3; blend with 100 gives 41.666..., rounded to 42.
        assertEquals(date("2026-03-14"),calculate(List.of(date("2026-01-01"),date("2026-01-11"),date("2026-01-31")),100,null,false));
    }
    @Test void onlyLatestFiveIntervalsCount() {
        var dates = List.of(today.minusDays(1050),today.minusDays(50),today.minusDays(40),
                today.minusDays(30),today.minusDays(20),today.minusDays(10),today);
        assertEquals(today.plusDays(37),calculate(dates,100,null,false));
    }
    @Test void volatileIntervalsAreBlendedWithDefault() {
        assertEquals(date("2027-03-27"),calculate(List.of(date("2024-01-01"),date("2024-01-02"),date("2026-01-01")),365,null,false));
    }
    @Test void latestFiveIntervalsRestartWeightsAtOne() {
        // Ignore the first 1000-day interval. Recent intervals are 10,20,30,40,50:
        // weighted mean = 550/15; blended with 100 = 55.666..., rounded to 56.
        var dates = List.of(today.minusDays(1150), today.minusDays(150),
                today.minusDays(140), today.minusDays(120), today.minusDays(90),
                today.minusDays(50), today);
        assertEquals(today.plusDays(56), calculate(dates, 100, null, false));
    }
    @Test void futureCompletionDoesNotReplaceLastValidCompletion() {
        assertEquals(today.plusDays(80),
                calculate(List.of(today.minusDays(20), today.plusDays(1)), 100, null, false));
    }
    @Test void duplicatesDoNotIncreaseHistoryCount() {
        assertEquals(date("2027-01-11"),calculate(List.of(date("2026-01-01"),date("2026-01-11"),date("2026-01-11")),365,null,false));
    }
    @Test void unorderedHistoryIsSorted() {
        assertEquals(date("2026-03-14"),calculate(List.of(date("2026-01-31"),date("2026-01-01"),date("2026-01-11")),100,null,false));
    }
    @Test void nullAndFutureHistoryAreIgnored() {
        assertNull(calculate(Arrays.asList(null,today.plusDays(1)),180,null,true));
    }
    @Test void overrideAlwaysWinsEvenBeforeLastCompletion() {
        assertEquals(today.minusDays(10),PredictionCalculator.calculate(List.of(today),180,null,today.minusDays(10),today,true));
    }
    @Test void summerAirconIntervalIsReduced() {
        assertEquals(date("2026-04-12"),calculate(List.of(date("2025-11-01")),180,null,true));
    }
    @Test void airconOutsideSeasonIsUnchanged() {
        assertEquals(date("2026-08-28"),calculate(List.of(date("2026-03-01")),180,null,true));
    }
    @Test void nonAirconIsNeverSeasonallyAdjusted() {
        assertEquals(date("2026-04-30"),calculate(List.of(date("2025-11-01")),180,null,false));
    }
    @ParameterizedTest @CsvSource({"58,2026-02-28","59,2026-02-23","180,2026-06-12","181,2026-07-01"})
    void seasonBoundariesUseUnadjustedDueDate(int interval, String expected) {
        assertEquals(date(expected),calculate(List.of(date("2026-01-01")),interval,null,true));
    }
    @Test void noHistoryAirconStillUsesLeaseAndSeason() {
        assertEquals(date("2026-04-12"),calculate(List.of(),180,date("2025-11-01"),true));
    }
    @Test void guardRepairsDateBeforeLatestCompletion() {
        // Positive intervals make this unreachable through normal input; test the defensive strategy directly.
        assertEquals(today.plusDays(1),PredictionCalculator.ensureAfterLastCompletion(today.minusDays(1),today));
    }
    @Test void invalidDefaultIsRejected() {
        assertThrows(IllegalArgumentException.class,()->calculate(List.of(),0,null,false));
    }
    @Test void noDataStatus() { assertEquals(PredictionStatus.NO_DATA,PredictionStatus.from(null,today)); }
    @ParameterizedTest @CsvSource({"-1,OVERDUE","0,DUE_SOON","7,DUE_SOON","8,WITHIN_30","30,WITHIN_30","31,ON_TRACK"})
    void statusBoundaries(int offset, PredictionStatus expected) {
        assertEquals(expected,PredictionStatus.from(today.plusDays(offset),today));
    }
}
