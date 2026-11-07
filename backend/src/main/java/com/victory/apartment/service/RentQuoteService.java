package com.victory.apartment.service;

import com.victory.apartment.model.Lease;
import com.victory.apartment.model.Room;
import com.victory.apartment.repository.LeaseRepository;
import com.victory.apartment.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Resolves the tenant and rent for a billing month. A bill requires a lease covering that month. */
@Service
public class RentQuoteService {

    public record RentQuote(Double rentAmount, String source, String leaseId) {}

    private static final DateTimeFormatter BILLING_MONTH = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH);

    private final RoomRepository rooms;
    private final LeaseRepository leases;
    private final RoomPriceService prices;

    public RentQuoteService(RoomRepository rooms, LeaseRepository leases, RoomPriceService prices) {
        this.rooms = rooms;
        this.leases = leases;
        this.prices = prices;
    }

    public static YearMonth parseBillingMonth(String billingMonth) {
        try {
            return YearMonth.parse(billingMonth, BILLING_MONTH);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new PriceRuleException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "Billing month must look like August 2026.");
        }
    }

    public RentQuote quote(String roomId, YearMonth month) {
        Room room = Optional.ofNullable(roomId).flatMap(rooms::findById)
                .orElseThrow(() -> new PriceRuleException(HttpStatus.BAD_REQUEST, "ROOM_NOT_FOUND", "Room not found."));

        // No proration, so one lease sets the whole month. A renewal in that month takes over from the old lease.
        Optional<Lease> lease = leases.findByRoomIdAndStatusIn(roomId, List.of("Active", "Terminated")).stream()
                .filter(l -> l.getRentAmount() != null && covers(l, month))
                .max(Comparator.comparing(Lease::getCheckInDate));
        if (lease.isEmpty()) {
            throw new PriceRuleException(HttpStatus.BAD_REQUEST, "NO_TENANT_FOR_BILLING_MONTH",
                    "Cannot issue a bill because no lease covers this room for the selected month.");
        }
        return new RentQuote(lease.get().getRentAmount(), "LEASE", lease.get().getId());
    }

    private static boolean covers(Lease lease, YearMonth month) {
        LocalDate end = exclusiveEnd(lease);
        return end != null
                && lease.getCheckInDate().isBefore(month.plusMonths(1).atDay(1))
                && end.isAfter(month.atDay(1));
    }

    private static LocalDate exclusiveEnd(Lease lease) {
        if ("Active".equals(lease.getStatus())) {
            return lease.getCheckOutDate();
        }
        // Leases terminated before this date was recorded have no known end, so they cover nothing.
        if (lease.getTerminatedDate() == null) {
            return null;
        }
        LocalDate dayAfterTermination = lease.getTerminatedDate().plusDays(1);
        return dayAfterTermination.isBefore(lease.getCheckOutDate()) ? dayAfterTermination : lease.getCheckOutDate();
    }
}
