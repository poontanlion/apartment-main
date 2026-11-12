package com.victory.apartment.service.maintenancepredictor;

import com.victory.apartment.model.*;
import com.victory.apartment.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaintenancePredictorServiceTest {
    private final RoomRepository rooms=mock(RoomRepository.class);
    private final MaintenanceTaskTypeRepository types=mock(MaintenanceTaskTypeRepository.class);
    private final MaintenanceLogRepository logs=mock(MaintenanceLogRepository.class);
    private final LeaseRepository leases=mock(LeaseRepository.class);
    private final MaintenancePredictionRepository predictions=mock(MaintenancePredictionRepository.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-07T18:00:00Z"),ZoneOffset.UTC);
    private final org.springframework.transaction.PlatformTransactionManager transactionManager =
            mock(org.springframework.transaction.PlatformTransactionManager.class);
    private final MaintenancePredictionDigestService digest = mock(MaintenancePredictionDigestService.class);
    private final MaintenancePredictorService service=new MaintenancePredictorService(rooms,types,logs,leases,predictions,clock,transactionManager,digest);
    private final MaintenanceTaskType type=new MaintenanceTaskType();
    @BeforeEach void setup() {
        type.setId("mtt-light");type.setCode("LIGHT");type.setIsRecurring(true);type.setDefaultIntervalDays(365);
        when(types.findById(type.getId())).thenReturn(Optional.of(type));
        when(rooms.existsById("rm-A101")).thenReturn(true);
        when(logs.findByRoomIdOrderByDateDesc("rm-A101")).thenReturn(List.of());
        when(leases.findByRoomIdAndStatus("rm-A101","Active")).thenReturn(List.of());
        when(predictions.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        when(transactionManager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
    }
    private Lease lease(LocalDate start) { var l=new Lease();l.setCheckInDate(start);return l; }
    @Test void recalculatePreservesOverrideAndIdentity() {
        var old=new MaintenancePrediction();old.setId("pred-existing");old.setRoomId("rm-A101");old.setTaskTypeId(type.getId());
        var override=LocalDate.of(2026,1,1);old.setOverrideDate(override);
        when(predictions.findByRoomIdAndTaskTypeId("rm-A101",type.getId())).thenReturn(Optional.of(old));
        var row=service.recalculate("rm-A101",type.getId()).orElseThrow();
        assertEquals(override,row.getOverrideDate());assertEquals(override,row.getPredictedDate());assertEquals("pred-existing",row.getId());
        assertEquals(LocalDateTime.of(2026,11,8,1,0),row.getComputedAt());
    }
    @Test void latestEligibleActiveLeaseIsChosenAndThaiTodayUsed() {
        when(leases.findByRoomIdAndStatus("rm-A101","Active")).thenReturn(List.of(
                lease(LocalDate.of(2025,1,1)),lease(LocalDate.of(2026,11,8)),lease(LocalDate.of(2026,11,9))));
        var row=service.recalculate("rm-A101",type.getId()).orElseThrow();
        assertEquals(LocalDate.of(2027,11,8),row.getPredictedDate());
        assertTrue(row.getId().matches("pred-[0-9a-f]{8}"));
    }
    @Test void legacyDateIsNeverParsedAndOtherTypesAreIgnored() {
        var legacy=new MaintenanceLog();legacy.setTaskTypeId(type.getId());legacy.setDate("2026-01-01");
        var other=new MaintenanceLog();other.setTaskTypeId("mtt-aircon");other.setCompletedDate(LocalDate.of(2026,1,1));
        var unmapped=new MaintenanceLog();unmapped.setCompletedDate(LocalDate.of(2026,1,1));
        when(logs.findByRoomIdOrderByDateDesc("rm-A101")).thenReturn(List.of(legacy,other,unmapped));
        assertNull(service.recalculate("rm-A101",type.getId()).orElseThrow().getPredictedDate());
    }
    @Test void mappedCompletedDateIsUsedWithoutLegacyDate() {
        var log=new MaintenanceLog();log.setTaskTypeId(type.getId());log.setCompletedDate(LocalDate.of(2026,1,1));log.setDate("invalid");
        when(logs.findByRoomIdOrderByDateDesc("rm-A101")).thenReturn(List.of(log));
        assertEquals(LocalDate.of(2027,1,1),service.recalculate("rm-A101",type.getId()).orElseThrow().getPredictedDate());
    }
    @Test void generalRepairIsSkippedWithoutSave() {
        type.setIsRecurring(false);type.setDefaultIntervalDays(null);
        assertTrue(service.recalculate("rm-A101",type.getId()).isEmpty());verifyNoInteractions(predictions);
    }
    @Test void missingRoomIsSkippedWithoutSave() {
        assertTrue(service.recalculate("missing",type.getId()).isEmpty());verifyNoInteractions(predictions);
    }
    @Test void missingTaskTypeIsSkippedWithoutSave() {
        assertTrue(service.recalculate("rm-A101", "missing-type").isEmpty());
        verifyNoInteractions(predictions, logs, leases);
    }
    @Test void serviceSelectsAirconSeasonalStrategyByCode() {
        type.setCode("AIRCON");
        type.setDefaultIntervalDays(180);
        when(leases.findByRoomIdAndStatus("rm-A101", "Active"))
                .thenReturn(List.of(lease(LocalDate.of(2025, 11, 1))));
        assertEquals(LocalDate.of(2026, 4, 12),
                service.recalculate("rm-A101", type.getId()).orElseThrow().getPredictedDate());
    }
    @Test void refreshProcessesEveryRoomWithRecurringTypesOnly() {
        var a=new Room();a.setId("a");var b=new Room();b.setId("b");var general=new MaintenanceTaskType();general.setIsRecurring(false);
        when(rooms.existsById("a")).thenReturn(true);when(rooms.existsById("b")).thenReturn(true);
        when(rooms.findAll()).thenReturn(List.of(a,b));when(types.findAll()).thenReturn(List.of(type,general));
        assertEquals(new MaintenancePredictorService.RefreshResult(2, 0), service.refreshAll());
        verify(predictions,times(2)).saveAndFlush(any());
        verify(predictions).findByRoomIdAndTaskTypeId("a",type.getId());verify(predictions).findByRoomIdAndTaskTypeId("b",type.getId());
    }

    private org.springframework.dao.DataIntegrityViolationException conflict(String name) {
        var sql = new java.sql.SQLException("duplicate", "23505");
        return new org.springframework.dao.DataIntegrityViolationException("conflict",
                new org.hibernate.exception.ConstraintViolationException("conflict", sql, name));
    }

    @Test void uniquePairConflictRetriesInNewTransactionAndKeepsWinnersOverride() {
        var winner = new MaintenancePrediction();
        winner.setId("pred-winner"); winner.setRoomId("rm-A101"); winner.setTaskTypeId(type.getId());
        winner.setOverrideDate(LocalDate.of(2026, 12, 1));
        when(predictions.findByRoomIdAndTaskTypeId("rm-A101", type.getId()))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(predictions.saveAndFlush(any())).thenThrow(conflict("uk_maintenance_predictions_room_task"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var row = service.recalculate("rm-A101", type.getId()).orElseThrow();
        assertEquals("pred-winner", row.getId());
        assertEquals(winner.getOverrideDate(), row.getPredictedDate());
        verify(transactionManager, times(2)).getTransaction(any());
        verify(transactionManager).rollback(any());
        verify(transactionManager).commit(any());
    }

    @Test void unrelatedConstraintViolationIsNotRetried() {
        when(predictions.saveAndFlush(any())).thenThrow(conflict("maintenance_predictions_pkey"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> service.recalculate("rm-A101", type.getId()));
        verify(predictions, times(1)).saveAndFlush(any());
        verify(transactionManager).rollback(any());
    }

    @Test void secondPairConflictEscapesAfterOnlyOneRetry() {
        when(predictions.saveAndFlush(any())).thenThrow(conflict("uk_maintenance_predictions_room_task"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> service.recalculate("rm-A101", type.getId()));
        verify(predictions, times(2)).saveAndFlush(any());
        verify(transactionManager, times(2)).rollback(any());
    }

    @Test void notificationFailureDoesNotFailRefresh() {
        when(rooms.findAll()).thenReturn(List.of());
        when(types.findAll()).thenReturn(List.of());
        when(digest.sendDailyDigest()).thenThrow(new IllegalStateException("notification unavailable"));
        assertEquals(new MaintenancePredictorService.RefreshResult(0, 0), service.refreshAll());
        verify(digest).sendDailyDigest();
    }
}
