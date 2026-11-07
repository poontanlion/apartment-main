package com.victory.apartment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.victory.apartment.repository.BillReportRow;
import com.victory.apartment.repository.UtilityBillRepository;
import com.victory.apartment.service.ReportService.Filter;
import com.victory.apartment.service.ReportService.GroupBy;
import com.victory.apartment.service.ReportService.Row;
import com.victory.apartment.service.ReportService.Summary;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

class ReportServiceTest {

    private final UtilityBillRepository repository = mock(UtilityBillRepository.class);
    private final ReportService service = new ReportService(repository);

    /** A paid August 2026 bill for room A101 by default; each test changes only what it is about. */
    private static final class Bill {
        String roomId = "rm-A101";
        String roomNumber = "A101";
        String tenantName = "Somchai";
        String billingMonth = "August 2026";
        Double rent = 5000.0;
        Double water = 90.0;
        Double electric = 400.0;
        Double commonFee = 300.0;
        Double total = 5790.0;
        String status = "Paid";
        String leaseTenantId;
        String leaseTenantName;
        String buildingId = "bld-1";

        Bill building(String id) { buildingId = id; return this; }
        Bill room(String id, String number) { roomId = id; roomNumber = number; return this; }
        Bill tenant(String name) { tenantName = name; return this; }
        Bill lease(String tenantId, String name) { leaseTenantId = tenantId; leaseTenantName = name; return this; }
        Bill month(String month) { billingMonth = month; return this; }
        Bill total(Double amount) { total = amount; return this; }
        Bill status(String value) { status = value; return this; }
        Bill amounts(Double rentAmount, Double waterAmount, Double electricAmount, Double fee, Double totalAmount) {
            rent = rentAmount; water = waterAmount; electric = electricAmount; commonFee = fee; total = totalAmount;
            return this;
        }

        BillReportRow row(int index) {
            return new BillReportRow("bill-" + index, null, roomId, roomNumber, tenantName, billingMonth,
                rent, water, electric, commonFee, total, status, leaseTenantId, leaseTenantName, buildingId);
        }
    }

    private static Bill bill() {
        return new Bill();
    }

    private Summary summarize(GroupBy groupBy, Bill... bills) {
        return summarize(Filter.of(groupBy), bills);
    }

    private Summary summarize(Filter filter, Bill... bills) {
        List<BillReportRow> rows = new ArrayList<>();
        for (int i = 0; i < bills.length; i++) {
            rows.add(bills[i].row(i));
        }
        when(repository.findAllForReport()).thenReturn(rows);
        return service.summarize(filter);
    }

    private static Filter monthRange(GroupBy groupBy, String from, String to) {
        return new Filter(groupBy, YearMonth.parse(from), YearMonth.parse(to), null, null, null);
    }

    private static Filter building(String buildingId) {
        return new Filter(GroupBy.UNIT, null, null, buildingId, null, null);
    }

    private static Filter status(String status) {
        return new Filter(GroupBy.UNIT, null, null, null, status, null);
    }

    private static Filter search(String search) {
        return new Filter(GroupBy.UNIT, null, null, null, null, search);
    }

    private static List<String> keys(Summary summary) {
        return summary.rows().stream().map(Row::key).toList();
    }

    private static List<String> labels(Summary summary) {
        return summary.rows().stream().map(Row::label).toList();
    }

    private static void assertAmount(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }

    @Test
    void groupsByUnit() {
        Summary summary = summarize(GroupBy.UNIT,
            bill().total(1000.0),
            bill().total(2000.0).month("September 2026"),
            bill().room("rm-A102", "A102").total(4000.0));

        assertThat(keys(summary)).containsExactly("rm-A101", "rm-A102");
        assertThat(labels(summary)).containsExactly("A101", "A102");
        assertThat(summary.rows().get(0).billCount()).isEqualTo(2);
        assertAmount(summary.rows().get(0).billed(), "3000");
        assertThat(summary.totals().billCount()).isEqualTo(3);
        assertAmount(summary.totals().billed(), "7000");
    }

    @Test
    void groupsByUnit_keepsDeletedRoomLabel() {
        Summary summary = summarize(GroupBy.UNIT, bill().room("rm-gone", "Z999"));

        assertThat(labels(summary)).containsExactly("Z999");
    }

    @Test
    void groupsByUnit_withoutRoomId_usesRoomNumber() {
        Summary summary = summarize(GroupBy.UNIT, bill().room(null, "A101"));

        assertThat(keys(summary)).containsExactly("A101");
        assertThat(labels(summary)).containsExactly("A101");
    }

    @Test
    void groupsByUnit_withoutAnyRoom_usesUnknown() {
        Summary summary = summarize(GroupBy.UNIT, bill().room(null, null));

        assertThat(keys(summary)).containsExactly("unknown");
        assertThat(labels(summary)).containsExactly("unknown");
    }

    @Test
    void groupsByTenant_sameNameDifferentLeases_staysSeparate() {
        Summary summary = summarize(GroupBy.TENANT,
            bill().lease("tenant-1", "Somchai"),
            bill().lease("tenant-2", "Somchai").room("rm-A102", "A102"));

        assertThat(keys(summary)).containsExactlyInAnyOrder("tenant-1", "tenant-2");
    }

    @Test
    void groupsByTenant_sameTenantAcrossRooms_staysTogether() {
        Summary summary = summarize(GroupBy.TENANT,
            bill().lease("tenant-1", "Somchai"),
            bill().lease("tenant-1", "Somchai").room("rm-A102", "A102"));

        assertThat(keys(summary)).containsExactly("tenant-1");
        assertThat(summary.rows().get(0).billCount()).isEqualTo(2);
    }

    @Test
    void groupsByTenant_withoutLease_usesName() {
        Summary summary = summarize(GroupBy.TENANT, bill().tenant("  Somchai "));

        assertThat(keys(summary)).containsExactly("name:Somchai");
        assertThat(labels(summary)).containsExactly("Somchai");
    }

    @Test
    void groupsByTenant_placeholderName_splitsByRoom() {
        Summary summary = summarize(GroupBy.TENANT,
            bill().tenant("Tenant"),
            bill().tenant("Tenant").room("rm-A102", "A102"));

        assertThat(keys(summary)).containsExactly("unidentified:rm-A101", "unidentified:rm-A102");
        assertThat(labels(summary)).containsExactly("A101", "A102");
    }

    @Test
    void groupsByTenant_blankOrPaddedPlaceholder_isUnidentified() {
        Summary summary = summarize(GroupBy.TENANT,
            bill().tenant("   "),
            bill().tenant(" Tenant ").room("rm-A102", "A102"),
            bill().tenant(null).room("rm-A103", "A103"));

        assertThat(keys(summary)).containsExactly(
            "unidentified:rm-A101", "unidentified:rm-A102", "unidentified:rm-A103");
    }

    @Test
    void groupsByTenant_realNameContainingPlaceholder_isKept() {
        Summary summary = summarize(GroupBy.TENANT, bill().tenant("Tenant Smith"));

        assertThat(keys(summary)).containsExactly("name:Tenant Smith");
    }

    @Test
    void groupsByTenant_labelUsesLatestRealName() {
        Summary summary = summarize(GroupBy.TENANT,
            bill().lease("tenant-1", "From Lease").tenant("Old Name").month("January 2026"),
            bill().lease("tenant-1", "From Lease").tenant("New Name").month("November 2026"),
            bill().lease("tenant-1", "From Lease").tenant("Tenant").month("December 2026"),
            bill().lease("tenant-1", "From Lease").tenant("Middle Name").month("June 2026"));

        assertThat(labels(summary)).containsExactly("New Name");
    }

    @Test
    void groupsByTenant_labelFallsBackToLeaseName() {
        Summary summary = summarize(GroupBy.TENANT, bill().lease("tenant-1", "From Lease").tenant("Tenant"));

        assertThat(labels(summary)).containsExactly("From Lease");
    }

    @Test
    void groupsByMonth_newestFirst() {
        Summary summary = summarize(GroupBy.MONTH,
            bill().month("January 2026"),
            bill().month("December 2025"),
            bill().month("February 2026"),
            bill().month("February 2026"));

        assertThat(keys(summary)).containsExactly("2026-02", "2026-01", "2025-12");
        assertThat(labels(summary)).containsExactly("2026-02", "2026-01", "2025-12");
        assertThat(summary.rows().get(0).billCount()).isEqualTo(2);
    }

    @Test
    void groupsByYear_newestFirst() {
        Summary summary = summarize(GroupBy.YEAR,
            bill().month("December 2025"),
            bill().month("January 2026"),
            bill().month("February 2026"));

        assertThat(keys(summary)).containsExactly("2026", "2025");
        assertThat(summary.rows().get(0).billCount()).isEqualTo(2);
    }

    @Test
    void oldBillWithoutRent_derivesRent() {
        Summary summary = summarize(GroupBy.UNIT, bill().amounts(null, 100.0, 200.0, null, 6000.0));

        Row row = summary.rows().get(0);
        assertAmount(row.rent(), "5400");
        assertAmount(row.commonFee(), "300");
        assertAmount(row.billed(), "6000");
    }

    @Test
    void storedTotalIsNotRecalculated() {
        Summary summary = summarize(GroupBy.UNIT, bill().amounts(5000.0, 90.0, 400.0, 300.0, 9999.0));

        assertAmount(summary.rows().get(0).billed(), "9999");
    }

    @Test
    void missingAmounts_countAsZero() {
        Summary summary = summarize(GroupBy.UNIT, bill().amounts(1000.0, null, null, 0.0, null));

        Row row = summary.rows().get(0);
        assertAmount(row.water(), "0");
        assertAmount(row.electric(), "0");
        assertAmount(row.commonFee(), "0");
        assertAmount(row.billed(), "0");
    }

    @Test
    void nonPaidStatuses_countAsOutstanding() {
        Summary summary = summarize(GroupBy.UNIT,
            bill().total(100.0).status("Paid"),
            bill().total(200.0).status("Pending"),
            bill().total(400.0).status("Overdue"),
            bill().total(800.0).status(null));

        assertAmount(summary.totals().billed(), "1500");
        assertAmount(summary.totals().paid(), "100");
        assertAmount(summary.totals().outstanding(), "1400");
    }

    @Test
    void paidPlusOutstanding_equalsBilled() {
        Summary summary = summarize(GroupBy.UNIT,
            bill().total(33.335).status("Paid"),
            bill().total(33.335).status("Pending"),
            bill().total(33.33).status("Pending"));

        Row row = summary.rows().get(0);
        assertAmount(row.billed(), "100.00");
        assertAmount(row.paid(), "33.34");
        assertAmount(row.outstanding(), "66.66");
    }

    @Test
    void roundsHalfUp() {
        Summary tiny = summarize(GroupBy.UNIT, bill().total(0.1), bill().total(0.2));
        assertAmount(tiny.totals().billed(), "0.30");

        Summary half = summarize(GroupBy.UNIT, bill().total(1.005));
        assertAmount(half.totals().billed(), "1.01");
    }

    @Test
    void totals_sumUnroundedAmounts() {
        Summary summary = summarize(GroupBy.UNIT,
            bill().total(0.004),
            bill().total(0.004).room("rm-A102", "A102"),
            bill().total(0.004).room("rm-A103", "A103"));

        assertAmount(summary.rows().get(0).billed(), "0.00");
        assertAmount(summary.totals().billed(), "0.01");
    }

    @Test
    void unreadableMonth_isExcludedAndCounted() {
        Summary summary = summarize(GroupBy.UNIT,
            bill().total(1000.0),
            bill().total(2000.0).month("2026-08"),
            bill().total(4000.0).month(null));

        assertThat(summary.skippedBills()).isEqualTo(2);
        assertThat(summary.totals().billCount()).isEqualTo(1);
        assertAmount(summary.totals().billed(), "1000");
    }

    @Test
    void labels_sortWithThaiCollation() {
        Summary summary = summarize(GroupBy.TENANT,
            bill().tenant("ข้าว"),
            bill().tenant("เกษม"),
            bill().tenant("กมล"));

        assertThat(labels(summary)).containsExactly("กมล", "เกษม", "ข้าว");
    }

    @Test
    void filtersMonthRange_inclusive() {
        Summary summary = summarize(monthRange(GroupBy.MONTH, "2026-01", "2026-02"),
            bill().month("December 2025"),
            bill().month("January 2026"),
            bill().month("February 2026"),
            bill().month("March 2026"));

        assertThat(keys(summary)).containsExactly("2026-02", "2026-01");
        assertThat(summary.totals().billCount()).isEqualTo(2);
    }

    @Test
    void filtersMonthRange_acrossYears() {
        Summary summary = summarize(monthRange(GroupBy.MONTH, "2025-12", "2026-01"),
            bill().month("November 2025"),
            bill().month("December 2025"),
            bill().month("January 2026"),
            bill().month("February 2026"));

        assertThat(keys(summary)).containsExactly("2026-01", "2025-12");
    }

    @Test
    void filtersMonthRange_openEnded() {
        Bill[] bills = {bill().month("January 2026"), bill().month("March 2026")};

        Filter onlyFrom = new Filter(GroupBy.MONTH, YearMonth.parse("2026-02"), null, null, null, null);
        assertThat(keys(summarize(onlyFrom, bills))).containsExactly("2026-03");

        Filter onlyTo = new Filter(GroupBy.MONTH, null, YearMonth.parse("2026-02"), null, null, null);
        assertThat(keys(summarize(onlyTo, bills))).containsExactly("2026-01");
    }

    @Test
    void filtersBuilding_excludesDeletedRoom() {
        Bill inBuilding = bill().building("bld-1");
        Bill otherBuilding = bill().building("bld-2").room("rm-B101", "B101");
        Bill deletedRoom = bill().building(null).room("rm-gone", "Z999");

        assertThat(labels(summarize(building("bld-1"), inBuilding, otherBuilding, deletedRoom)))
            .containsExactly("A101");
        assertThat(labels(summarize(building("All"), inBuilding, otherBuilding, deletedRoom)))
            .containsExactly("A101", "B101", "Z999");
    }

    @Test
    void filtersStatus_pendingMeansNotPaid() {
        Bill[] bills = {
            bill().total(100.0).status("Paid"),
            bill().total(200.0).status("Pending"),
            bill().total(400.0).status("Overdue"),
            bill().total(800.0).status(null),
        };

        assertAmount(summarize(status("Pending"), bills).totals().billed(), "1400");
        assertAmount(summarize(status("Paid"), bills).totals().billed(), "100");
        assertAmount(summarize(status("All"), bills).totals().billed(), "1500");
    }

    @Test
    void search_matchesRoomOrTenantIgnoringCase() {
        Bill a101 = bill().room("rm-A101", "A101").tenant("Malee");
        Bill a102 = bill().room("rm-A102", "A102").tenant("Somchai");

        assertThat(labels(summarize(search("a101"), a101, a102))).containsExactly("A101");
        assertThat(labels(summarize(search("somchai"), a101, a102))).containsExactly("A102");
    }

    @Test
    void search_matchesLeaseNameShownAsLabel() {
        Summary summary = summarize(
            new Filter(GroupBy.TENANT, null, null, null, null, "from lease"),
            bill().lease("tenant-1", "From Lease").tenant("Tenant"));

        assertThat(labels(summary)).containsExactly("From Lease");
    }

    @Test
    void search_trimsAndTreatsBlankAsNoFilter() {
        Bill a101 = bill().room("rm-A101", "A101");
        Bill a102 = bill().room("rm-A102", "A102");

        assertThat(labels(summarize(search("  A101 "), a101, a102))).containsExactly("A101");
        assertThat(labels(summarize(search("   "), a101, a102))).containsExactly("A101", "A102");
    }

    @Test
    void search_treatsPercentLiterally() {
        Summary summary = summarize(search("%"), bill());

        assertThat(summary.rows()).isEmpty();
    }

    @Test
    void noMatches_returnsEmptyRowsAndZeroTotals() {
        Summary summary = summarize(search("nobody"), bill());

        assertThat(summary.rows()).isEmpty();
        assertThat(summary.totals().billCount()).isZero();
        assertAmount(summary.totals().billed(), "0");
        assertAmount(summary.totals().paid(), "0");
        assertAmount(summary.totals().outstanding(), "0");
    }

    @Test
    void skippedBills_respectsOtherFilters() {
        Summary summary = summarize(building("bld-1"),
            bill().month("2026-08").building("bld-1"),
            bill().month("2026-08").building("bld-2"));

        assertThat(summary.skippedBills()).isEqualTo(1);
    }

    @Test
    void skippedBills_countedWhenMonthRangeSet() {
        Summary summary = summarize(monthRange(GroupBy.UNIT, "2026-01", "2026-02"), bill().month("2026-08"));

        assertThat(summary.skippedBills()).isEqualTo(1);
    }

    @Test
    void duplicateRoomMonths_countsPairs() {
        Summary summary = summarize(GroupBy.UNIT,
            bill().total(100.0),
            bill().total(100.0),
            bill().total(100.0),
            bill().total(100.0).room("rm-A102", "A102"),
            bill().total(100.0).room("rm-A102", "A102"),
            bill().total(100.0).room("rm-A103", "A103"),
            bill().total(100.0).month("September 2026"));

        assertThat(summary.duplicateRoomMonths()).isEqualTo(2);
        assertAmount(summary.totals().billed(), "700");
    }

    @Test
    void duplicateRoomMonths_respectsFilters() {
        Summary summary = summarize(monthRange(GroupBy.UNIT, "2026-08", "2026-08"),
            bill(),
            bill().month("September 2026"),
            bill().month("September 2026"));

        assertThat(summary.duplicateRoomMonths()).isZero();
    }

    @Test
    void reportsGroupBy() {
        assertThat(summarize(GroupBy.YEAR, bill()).groupBy()).isEqualTo(GroupBy.YEAR);
    }
}
