package com.victory.apartment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.victory.apartment.config.PricingConfig;
import com.victory.apartment.controller.PriceRuleExceptionHandler;
import com.victory.apartment.model.Room;
import com.victory.apartment.model.RoomPrice;
import com.victory.apartment.repository.RoomPriceRepository;
import com.victory.apartment.repository.RoomRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class RoomPriceServiceTest {

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final String ROOM_ID = "rm-1";

    private final RoomPriceRepository prices = mock(RoomPriceRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final ActivityLogService log = mock(ActivityLogService.class);
    private final List<RoomPrice> rows = new ArrayList<>();
    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room();
        room.setId(ROOM_ID);
        room.setRoomNumber("A101");
        room.setPrice(6000.0);

        when(rooms.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(room));
        when(rooms.findById(ROOM_ID)).thenReturn(Optional.of(room));

        // An in-memory stand-in for the table, so tests assert on the resulting rows.
        when(prices.findByRoomIdOrderByEffectiveMonthDesc(anyString())).thenAnswer(call ->
            rows.stream()
                .filter(r -> r.getRoomId().equals(call.getArgument(0)))
                .sorted(Comparator.comparing(RoomPrice::getEffectiveMonth).reversed())
                .toList());
        when(prices.findFirstByRoomIdAndEffectiveMonthLessThanEqualOrderByEffectiveMonthDesc(anyString(), any()))
            .thenAnswer(call -> rows.stream()
                .filter(r -> r.getRoomId().equals(call.getArgument(0)))
                .filter(r -> !r.getEffectiveMonth().isAfter(call.getArgument(1)))
                .max(Comparator.comparing(RoomPrice::getEffectiveMonth)));
        when(prices.findByRoomIdAndEffectiveMonth(anyString(), any())).thenAnswer(call ->
            rows.stream()
                .filter(r -> r.getRoomId().equals(call.getArgument(0)))
                .filter(r -> r.getEffectiveMonth().equals(call.getArgument(1)))
                .findFirst());
        when(prices.findByEffectiveMonthLessThanEqualOrderByRoomIdAscEffectiveMonthDesc(any())).thenAnswer(call ->
            rows.stream()
                .filter(r -> !r.getEffectiveMonth().isAfter(call.getArgument(0)))
                .sorted(Comparator.comparing(RoomPrice::getRoomId)
                    .thenComparing(Comparator.comparing(RoomPrice::getEffectiveMonth).reversed()))
                .toList());
        when(prices.findById(anyString())).thenAnswer(call ->
            rows.stream().filter(r -> call.getArgument(0).equals(r.getId())).findFirst());
        when(prices.save(any(RoomPrice.class))).thenAnswer(call -> {
            RoomPrice saved = call.getArgument(0);
            if (!rows.contains(saved)) rows.add(saved);
            return saved;
        });
    }

    private RoomPriceService serviceAt(String instant) {
        return new RoomPriceService(prices, rooms, log, Clock.fixed(Instant.parse(instant), BANGKOK));
    }

    /** Mid-November 2026 in Bangkok. */
    private RoomPriceService service() {
        return serviceAt("2026-11-15T05:00:00Z");
    }

    private RoomPrice row(String id, String month, double price) {
        RoomPrice r = new RoomPrice();
        r.setId(id);
        r.setRoomId(ROOM_ID);
        r.setEffectiveMonth(LocalDate.parse(month));
        r.setPrice(price);
        rows.add(r);
        return r;
    }

    private double priceInRow(String month) {
        return rows.stream()
            .filter(r -> r.getEffectiveMonth().equals(LocalDate.parse(month)))
            .findFirst().orElseThrow().getPrice();
    }

    private void assertRule(Runnable call, HttpStatus status, String code) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(PriceRuleException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(status);
                assertThat(e.getCode()).isEqualTo(code);
            });
    }

    @Test
    void priceFor_usesLatestRowNotAfterMonth() {
        row("p1", "2000-01-01", 6000);
        row("p2", "2026-12-01", 6500);

        assertThat(service().priceFor(room, YearMonth.of(2026, 11))).isEqualTo(6000.0);
        assertThat(service().priceFor(room, YearMonth.of(2026, 12))).isEqualTo(6500.0);
        assertThat(service().priceFor(room, YearMonth.of(2027, 1))).isEqualTo(6500.0);
    }

    @Test
    void priceFor_fallsBackToRoomPriceWithoutRows() {
        assertThat(service().priceFor(room, YearMonth.of(2026, 11))).isEqualTo(6000.0);
    }

    @Test
    void setPrice_rejectsPastMonth() {
        assertRule(() -> service().setPrice(ROOM_ID, 6500.0, YearMonth.of(2026, 10)),
            HttpStatus.BAD_REQUEST, "PRICE_MONTH_IN_PAST");
        assertThat(rows).isEmpty();
        verify(prices, never()).save(any());
    }

    @Test
    void setPrice_rejectsNonPositivePrice() {
        for (Double bad : new Double[] {0.0, -1.0, null, Double.NaN}) {
            assertRule(() -> service().setPrice(ROOM_ID, bad, YearMonth.of(2026, 12)),
                HttpStatus.BAD_REQUEST, "INVALID_PRICE");
        }
        verify(prices, never()).save(any());
    }

    @Test
    void setPrice_createsBaselineOnFirstChange() {
        service().setPrice(ROOM_ID, 6500.0, YearMonth.of(2026, 12));

        assertThat(rows).hasSize(2);
        assertThat(priceInRow("2000-01-01")).isEqualTo(6000.0);
        assertThat(priceInRow("2026-12-01")).isEqualTo(6500.0);
        assertThat(rows).allSatisfy(r -> assertThat(r.getId()).startsWith("rp-"));
    }

    @Test
    void setPrice_baselineKeepsOldPriceForEarlierMonthsAfterCurrentMonthChange() {
        RoomPriceService service = service();

        service.setPrice(ROOM_ID, 6500.0, YearMonth.of(2026, 11));

        assertThat(service.priceFor(room, YearMonth.of(2026, 10))).isEqualTo(6000.0);
        assertThat(service.priceFor(room, YearMonth.of(2026, 11))).isEqualTo(6500.0);
    }

    @Test
    void setPrice_replacesSameMonth() {
        row("p1", "2000-01-01", 6000);
        row("p2", "2026-12-01", 6500);

        service().setPrice(ROOM_ID, 6700.0, YearMonth.of(2026, 12));

        assertThat(rows).hasSize(2);
        assertThat(priceInRow("2026-12-01")).isEqualTo(6700.0);
    }

    @Test
    void setPrice_futureMonthKeepsRoomPrice() {
        service().setPrice(ROOM_ID, 6500.0, YearMonth.of(2026, 12));

        assertThat(room.getPrice()).isEqualTo(6000.0);
    }

    @Test
    void setPrice_currentMonthUpdatesRoomPrice() {
        service().setPrice(ROOM_ID, 6500.0, YearMonth.of(2026, 11));

        assertThat(room.getPrice()).isEqualTo(6500.0);
        verify(rooms).save(room);
        verify(log).log(anyString(), anyString());
    }

    @Test
    void setPrice_unknownRoomIs404() {
        when(rooms.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        assertRule(() -> service().setPrice("missing", 6500.0, YearMonth.of(2026, 12)),
            HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND");
    }

    @Test
    void deleteScheduled_rejectsMonthThatArrived() {
        RoomPrice december = row("p2", "2026-12-01", 6500);

        assertRule(() -> serviceAt("2026-12-01T05:00:00Z").deleteScheduled(ROOM_ID, "p2"),
            HttpStatus.CONFLICT, "PRICE_NOT_SCHEDULED");
        verify(prices, never()).delete(december);
    }

    @Test
    void deleteScheduled_removesFutureRow() {
        RoomPrice december = row("p2", "2026-12-01", 6500);

        service().deleteScheduled(ROOM_ID, "p2");

        verify(prices).delete(december);
    }

    @Test
    void deleteScheduled_rowOfAnotherRoomIs404() {
        RoomPrice other = row("p9", "2026-12-01", 9000);
        other.setRoomId("rm-other");

        assertRule(() -> service().deleteScheduled(ROOM_ID, "p9"),
            HttpStatus.NOT_FOUND, "PRICE_NOT_FOUND");
        verify(prices, never()).delete(other);
    }

    @Test
    void currentMonth_usesBangkokAtMonthBoundary() {
        // 00:30 on 1 December in Bangkok, while UTC is still in November.
        assertThat(serviceAt("2026-11-30T17:30:00Z").currentMonth()).isEqualTo(YearMonth.of(2026, 12));
    }

    @Test
    void clockBean_usesBangkok() {
        assertThat(new PricingConfig().clock().getZone()).isEqualTo(BANGKOK);
    }

    @Test
    void syncCurrentPrices_appliesArrivedPriceOnce() {
        row("p1", "2000-01-01", 6000);
        row("p2", "2026-12-01", 6500);
        RoomPriceService service = serviceAt("2026-12-01T05:00:00Z");

        service.syncCurrentPrices();
        service.syncCurrentPrices();

        assertThat(room.getPrice()).isEqualTo(6500.0);
        verify(rooms, times(1)).save(room);
    }

    @Test
    void syncCurrentPrices_leavesFuturePriceAlone() {
        row("p1", "2000-01-01", 6000);
        row("p2", "2026-12-01", 6500);

        service().syncCurrentPrices();

        assertThat(room.getPrice()).isEqualTo(6000.0);
        verify(rooms, never()).save(any());
    }

    @Test
    void history_withoutRowsReturnsBaselineWithoutWriting() {
        List<RoomPrice> history = service().history(room);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getId()).isNull();
        assertThat(history.get(0).getEffectiveMonth()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(history.get(0).getPrice()).isEqualTo(6000.0);
        verify(prices, never()).save(any());
    }

    @Test
    void history_returnsNewestFirst() {
        row("p1", "2000-01-01", 6000);
        row("p2", "2026-12-01", 6500);

        assertThat(service().history(room)).extracting(RoomPrice::getId).containsExactly("p2", "p1");
    }

    @Test
    void exceptionHandler_returnsStatusCodeAndMessage() {
        ResponseEntity<Map<String, String>> response = new PriceRuleExceptionHandler().handle(
            new PriceRuleException(HttpStatus.CONFLICT, "PRICE_NOT_SCHEDULED", "Too late"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "PRICE_NOT_SCHEDULED").containsEntry("message", "Too late");
    }
}
