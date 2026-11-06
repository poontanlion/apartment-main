package com.victory.apartment.service;

import com.victory.apartment.model.Room;
import com.victory.apartment.model.RoomPrice;
import com.victory.apartment.repository.RoomPriceRepository;
import com.victory.apartment.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RoomPriceService {

    /** Effective month of a room's first row: its price before any recorded change. */
    private static final LocalDate BASELINE_MONTH = LocalDate.of(2000, 1, 1);

    private final RoomPriceRepository prices;
    private final RoomRepository rooms;
    private final ActivityLogService logService;
    private final Clock clock;

    public RoomPriceService(RoomPriceRepository prices, RoomRepository rooms, ActivityLogService logService, Clock clock) {
        this.prices = prices;
        this.rooms = rooms;
        this.logService = logService;
        this.clock = clock;
    }

    public YearMonth currentMonth() {
        return YearMonth.now(clock);
    }

    public Double priceFor(Room room, YearMonth month) {
        return prices.findFirstByRoomIdAndEffectiveMonthLessThanEqualOrderByEffectiveMonthDesc(room.getId(), month.atDay(1))
                .map(RoomPrice::getPrice)
                .orElse(room.getPrice());
    }

    @Transactional
    public RoomPrice setPrice(String roomId, Double price, YearMonth month) {
        if (price == null || !Double.isFinite(price) || price <= 0) {
            throw new PriceRuleException(HttpStatus.BAD_REQUEST, "INVALID_PRICE", "Price must be greater than zero.");
        }
        YearMonth current = currentMonth();
        if (month.isBefore(current)) {
            throw new PriceRuleException(HttpStatus.BAD_REQUEST, "PRICE_MONTH_IN_PAST", "A price cannot start in a past month.");
        }
        Room room = rooms.findByIdForUpdate(roomId)
                .orElseThrow(() -> new PriceRuleException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room not found."));

        // Without this row, months before the first change would read the new Room.price.
        if (room.getPrice() != null && prices.findByRoomIdOrderByEffectiveMonthDesc(roomId).isEmpty()) {
            prices.save(newRow(roomId, room.getPrice(), BASELINE_MONTH));
        }

        LocalDate effective = month.atDay(1);
        RoomPrice row = prices.findByRoomIdAndEffectiveMonth(roomId, effective)
                .orElseGet(() -> newRow(roomId, price, effective));
        row.setPrice(price);
        RoomPrice saved = prices.save(row);

        if (month.equals(current)) {
            room.setPrice(price);
            rooms.save(room);
            logService.log("Room Price Updated", String.format("Unit %s price set to %.0f THB from %s", room.getRoomNumber(), price, month));
        }
        return saved;
    }

    @Transactional
    public void deleteScheduled(String roomId, String priceId) {
        RoomPrice row = prices.findById(priceId)
                .filter(r -> r.getRoomId().equals(roomId))
                .orElseThrow(() -> new PriceRuleException(HttpStatus.NOT_FOUND, "PRICE_NOT_FOUND", "Price not found."));
        if (!YearMonth.from(row.getEffectiveMonth()).isAfter(currentMonth())) {
            throw new PriceRuleException(HttpStatus.CONFLICT, "PRICE_NOT_SCHEDULED", "Only prices for future months can be deleted.");
        }
        prices.delete(row);
    }

    /** Newest first. A room with no recorded change shows its current price as the baseline, without writing it. */
    public List<RoomPrice> history(Room room) {
        List<RoomPrice> rows = prices.findByRoomIdOrderByEffectiveMonthDesc(room.getId());
        if (!rows.isEmpty() || room.getPrice() == null) {
            return rows;
        }
        RoomPrice baseline = new RoomPrice();
        baseline.setRoomId(room.getId());
        baseline.setPrice(room.getPrice());
        baseline.setEffectiveMonth(BASELINE_MONTH);
        return List.of(baseline);
    }

    /** Safe to repeat and to run on several replicas at once: each run writes the same result. */
    @Transactional
    public void syncCurrentPrices() {
        Set<String> seen = new HashSet<>();
        for (RoomPrice row : prices.findByEffectiveMonthLessThanEqualOrderByRoomIdAscEffectiveMonthDesc(currentMonth().atDay(1))) {
            if (!seen.add(row.getRoomId())) continue; // rows are newest first per room
            rooms.findById(row.getRoomId())
                    .filter(room -> !row.getPrice().equals(room.getPrice()))
                    .ifPresent(room -> {
                        room.setPrice(row.getPrice());
                        rooms.save(room);
                    });
        }
    }

    private RoomPrice newRow(String roomId, Double price, LocalDate effectiveMonth) {
        RoomPrice row = new RoomPrice();
        row.setId("rp-" + UUID.randomUUID().toString().substring(0, 8));
        row.setRoomId(roomId);
        row.setPrice(price);
        row.setEffectiveMonth(effectiveMonth);
        row.setCreatedAt(LocalDateTime.now(clock));
        return row;
    }
}
