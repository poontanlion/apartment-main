package com.victory.apartment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.victory.apartment.model.Lease;
import com.victory.apartment.model.Room;
import com.victory.apartment.repository.LeaseRepository;
import com.victory.apartment.repository.RoomRepository;
import com.victory.apartment.service.RentQuoteService.RentQuote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

class RentQuoteServiceTest {

    private static final String ROOM_ID = "rm-1";
    private static final YearMonth DECEMBER = YearMonth.of(2026, 12);

    private final RoomRepository rooms = mock(RoomRepository.class);
    private final LeaseRepository leases = mock(LeaseRepository.class);
    private final RoomPriceService prices = mock(RoomPriceService.class);
    private final RentQuoteService service = new RentQuoteService(rooms, leases, prices);
    private final List<Lease> stored = new ArrayList<>();
    private final Room room = new Room();

    @BeforeEach
    void setUp() {
        room.setId(ROOM_ID);
        when(rooms.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(prices.priceFor(any(), any())).thenReturn(6500.0);
        when(leases.findByRoomIdAndStatusIn(anyString(), any())).thenAnswer(call -> {
            Collection<String> statuses = call.getArgument(1);
            return stored.stream().filter(l -> statuses.contains(l.getStatus())).toList();
        });
    }

    private Lease lease(String id, String status, String start, String end, Double rent) {
        Lease l = new Lease();
        l.setId(id);
        l.setStatus(status);
        l.setCheckInDate(LocalDate.parse(start));
        l.setCheckOutDate(LocalDate.parse(end));
        l.setRentAmount(rent);
        stored.add(l);
        return l;
    }

    private void assertRoomPrice(RentQuote quote) {
        assertThat(quote.rentAmount()).isEqualTo(6500.0);
        assertThat(quote.source()).isEqualTo("ROOM_PRICE");
        assertThat(quote.leaseId()).isNull();
    }

    @Test
    void activeLeaseWinsOverRoomPrice() {
        lease("ls-1", "Active", "2026-01-15", "2027-01-15", 6000.0);

        RentQuote quote = service.quote(ROOM_ID, DECEMBER);

        assertThat(quote.rentAmount()).isEqualTo(6000.0);
        assertThat(quote.source()).isEqualTo("LEASE");
        assertThat(quote.leaseId()).isEqualTo("ls-1");
    }

    @Test
    void leaseStartingMidMonthCovers() {
        lease("ls-1", "Active", "2026-12-20", "2027-12-20", 6000.0);

        assertThat(service.quote(ROOM_ID, DECEMBER).rentAmount()).isEqualTo(6000.0);
    }

    @Test
    void twoLeasesUseLaterStart() {
        lease("ls-old", "Active", "2026-01-15", "2026-12-15", 6000.0);
        lease("ls-new", "Active", "2026-12-15", "2027-12-15", 6200.0);

        RentQuote quote = service.quote(ROOM_ID, DECEMBER);

        assertThat(quote.rentAmount()).isEqualTo(6200.0);
        assertThat(quote.leaseId()).isEqualTo("ls-new");
    }

    @Test
    void leaseEndingOnFirstDoesNotCover() {
        lease("ls-1", "Active", "2026-01-01", "2026-12-01", 6000.0);

        assertRoomPrice(service.quote(ROOM_ID, DECEMBER));
    }

    @Test
    void leaseStartingNextMonthDoesNotCover() {
        lease("ls-1", "Active", "2027-01-01", "2028-01-01", 6000.0);

        assertRoomPrice(service.quote(ROOM_ID, DECEMBER));
    }

    @Test
    void terminatedLeaseCoversTerminationMonth() {
        lease("ls-1", "Terminated", "2026-01-15", "2027-01-15", 6000.0).setTerminatedDate(LocalDate.of(2026, 12, 15));

        RentQuote quote = service.quote(ROOM_ID, DECEMBER);

        assertThat(quote.rentAmount()).isEqualTo(6000.0);
        assertThat(quote.source()).isEqualTo("LEASE");
    }

    @Test
    void terminatedLeaseDoesNotCoverLaterMonth() {
        lease("ls-1", "Terminated", "2026-01-15", "2027-02-15", 6000.0).setTerminatedDate(LocalDate.of(2026, 12, 15));

        assertRoomPrice(service.quote(ROOM_ID, YearMonth.of(2027, 1)));
    }

    @Test
    void terminatedOnLastDayDoesNotReachNextMonth() {
        lease("ls-1", "Terminated", "2026-01-15", "2027-01-15", 6000.0).setTerminatedDate(LocalDate.of(2026, 11, 30));

        assertRoomPrice(service.quote(ROOM_ID, DECEMBER));
    }

    @Test
    void terminatedAfterLeaseEndStopsAtLeaseEnd() {
        lease("ls-1", "Terminated", "2026-01-15", "2026-12-01", 6000.0).setTerminatedDate(LocalDate.of(2026, 12, 20));

        assertRoomPrice(service.quote(ROOM_ID, DECEMBER));
    }

    @Test
    void terminatedWithoutDateUsesRoomPrice() {
        lease("ls-1", "Terminated", "2026-01-15", "2027-01-15", 6000.0);

        assertRoomPrice(service.quote(ROOM_ID, DECEMBER));
    }

    @Test
    void expiredLeaseIsIgnored() {
        lease("ls-1", "Expired", "2026-01-15", "2027-01-15", 6000.0);

        assertRoomPrice(service.quote(ROOM_ID, DECEMBER));
    }

    @Test
    void leaseWithoutRentFallsBackToRoomPrice() {
        lease("ls-1", "Active", "2026-01-15", "2027-01-15", null);

        assertRoomPrice(service.quote(ROOM_ID, DECEMBER));
    }

    @Test
    void crossYearMonthUsesRoomPriceOfThatMonth() {
        when(prices.priceFor(room, YearMonth.of(2027, 1))).thenReturn(7000.0);

        assertThat(service.quote(ROOM_ID, YearMonth.of(2027, 1)).rentAmount()).isEqualTo(7000.0);
    }

    @Test
    void unknownRoomIs400() {
        when(rooms.findById("missing")).thenReturn(Optional.empty());

        for (String roomId : new String[] {"missing", null}) {
            assertThatThrownBy(() -> service.quote(roomId, DECEMBER))
                .isInstanceOfSatisfying(PriceRuleException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getCode()).isEqualTo("ROOM_NOT_FOUND");
                });
        }
    }

    @Test
    void roomWithoutAnyPriceIs400() {
        when(prices.priceFor(any(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.quote(ROOM_ID, DECEMBER))
            .isInstanceOfSatisfying(PriceRuleException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getCode()).isEqualTo("RENT_UNAVAILABLE");
            });
    }

    @Test
    void parseBillingMonth_acceptsEnglishMonthAndYear() {
        assertThat(RentQuoteService.parseBillingMonth("August 2026")).isEqualTo(YearMonth.of(2026, 8));
    }

    @Test
    void parseBillingMonth_rejectsOtherFormats() {
        for (String bad : new String[] {"2026-08", "สิงหาคม 2026", null}) {
            assertThatThrownBy(() -> RentQuoteService.parseBillingMonth(bad))
                .isInstanceOfSatisfying(PriceRuleException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getCode()).isEqualTo("INVALID_MONTH");
                });
        }
    }
}
