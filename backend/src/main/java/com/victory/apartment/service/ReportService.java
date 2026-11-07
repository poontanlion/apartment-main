package com.victory.apartment.service;

import com.victory.apartment.repository.BillReportRow;
import com.victory.apartment.repository.UtilityBillRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportService {

    /** What the bill form stores when it finds no tenant for the room. */
    private static final String PLACEHOLDER_TENANT = "Tenant";

    public enum GroupBy { UNIT, TENANT, MONTH, YEAR }

    public record Filter(GroupBy groupBy, YearMonth from, YearMonth to, String buildingId, String status, String search) {
        public static Filter of(GroupBy groupBy) {
            return new Filter(groupBy, null, null, null, null, null);
        }
    }

    public record Totals(long billCount, BigDecimal rent, BigDecimal water, BigDecimal electric,
                         BigDecimal commonFee, BigDecimal billed, BigDecimal paid, BigDecimal outstanding) {}

    public record Row(String key, String label, long billCount, BigDecimal rent, BigDecimal water, BigDecimal electric,
                      BigDecimal commonFee, BigDecimal billed, BigDecimal paid, BigDecimal outstanding) {}

    public record Summary(GroupBy groupBy, Totals totals, List<Row> rows, long skippedBills, long duplicateRoomMonths) {}

    private final UtilityBillRepository bills;

    public ReportService(UtilityBillRepository bills) {
        this.bills = bills;
    }

    public Summary summarize(Filter filter) {
        Map<String, Group> groups = new LinkedHashMap<>();
        Group overall = new Group("", "");
        long skippedBills = 0;

        for (BillReportRow bill : bills.findAllForReport()) {
            YearMonth month = readableMonth(bill);
            if (month == null) {
                skippedBills++;
                continue;
            }
            overall.add(bill);
            Group group = groupFor(filter.groupBy(), bill, month, groups);
            group.add(bill);
            if (filter.groupBy() == GroupBy.TENANT) {
                group.offerName(realTenantName(bill), month);
            }
        }

        List<Row> rows = groups.values().stream()
            .sorted(ordering(filter.groupBy()))
            .map(Group::toRow)
            .toList();
        return new Summary(filter.groupBy(), overall.toTotals(), rows, skippedBills, 0);
    }

    private static YearMonth readableMonth(BillReportRow bill) {
        try {
            return RentQuoteService.parseBillingMonth(bill.billingMonth());
        } catch (PriceRuleException unreadable) {
            return null;
        }
    }

    private static Group groupFor(GroupBy groupBy, BillReportRow bill, YearMonth month, Map<String, Group> groups) {
        String key = switch (groupBy) {
            case UNIT -> roomKey(bill);
            case TENANT -> tenantKey(bill);
            case MONTH -> month.toString();
            case YEAR -> String.valueOf(month.getYear());
        };
        return groups.computeIfAbsent(key, k -> new Group(k, switch (groupBy) {
            case UNIT -> roomLabel(bill);
            case TENANT -> tenantFallbackLabel(bill);
            case MONTH, YEAR -> k;
        }));
    }

    /** Used until a bill offers a real name: the lease's name, or the room for an unidentified tenant. */
    private static String tenantFallbackLabel(BillReportRow bill) {
        if (bill.leaseTenantId() != null) {
            return bill.leaseTenantName() != null ? bill.leaseTenantName() : bill.leaseTenantId();
        }
        String name = realTenantName(bill);
        return name != null ? name : roomLabel(bill);
    }

    private static String roomKey(BillReportRow bill) {
        if (bill.roomId() != null) {
            return bill.roomId();
        }
        return bill.roomNumber() != null ? bill.roomNumber() : "unknown";
    }

    private static String roomLabel(BillReportRow bill) {
        return bill.roomNumber() != null ? bill.roomNumber() : roomKey(bill);
    }

    private static String tenantKey(BillReportRow bill) {
        if (bill.leaseTenantId() != null) {
            return bill.leaseTenantId();
        }
        String name = realTenantName(bill);
        return name != null ? "name:" + name : "unidentified:" + roomKey(bill);
    }

    private static String realTenantName(BillReportRow bill) {
        if (bill.tenantName() == null) {
            return null;
        }
        String name = bill.tenantName().trim();
        return name.isEmpty() || name.equals(PLACEHOLDER_TENANT) ? null : name;
    }

    private static Comparator<Group> ordering(GroupBy groupBy) {
        if (groupBy == GroupBy.MONTH || groupBy == GroupBy.YEAR) {
            return Comparator.comparing((Group group) -> group.key).reversed();
        }
        Collator thai = Collator.getInstance(Locale.forLanguageTag("th"));
        return Comparator.<Group, String>comparing(group -> group.label, thai).thenComparing(group -> group.key);
    }

    private static BigDecimal amount(Double value) {
        return value != null ? BigDecimal.valueOf(value) : BigDecimal.ZERO;
    }

    private static BigDecimal rounded(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Keeps exact sums, so rounding happens once on each figure the report shows. */
    private static final class Group {
        private final String key;
        private String label;
        private YearMonth namedAt;
        private long billCount;
        private BigDecimal rent = BigDecimal.ZERO;
        private BigDecimal water = BigDecimal.ZERO;
        private BigDecimal electric = BigDecimal.ZERO;
        private BigDecimal commonFee = BigDecimal.ZERO;
        private BigDecimal billed = BigDecimal.ZERO;
        private BigDecimal paid = BigDecimal.ZERO;

        private Group(String key, String label) {
            this.key = key;
            this.label = label;
        }

        /** The newest bill with a real name labels the row. */
        private void offerName(String name, YearMonth month) {
            if (name != null && (namedAt == null || month.isAfter(namedAt))) {
                label = name;
                namedAt = month;
            }
        }

        private void add(BillReportRow bill) {
            billCount++;
            rent = rent.add(bill.rentAmount() != null
                ? BigDecimal.valueOf(bill.rentAmount())
                : BigDecimal.valueOf(BillAmounts.rentInsideTotal(
                    bill.totalAmount(), bill.waterAmount(), bill.electricAmount(), bill.commonFee())));
            water = water.add(amount(bill.waterAmount()));
            electric = electric.add(amount(bill.electricAmount()));
            commonFee = commonFee.add(BigDecimal.valueOf(BillAmounts.commonFeeOrDefault(bill.commonFee())));
            billed = billed.add(amount(bill.totalAmount()));
            if ("Paid".equals(bill.status())) {
                paid = paid.add(amount(bill.totalAmount()));
            }
        }

        /** Outstanding comes from the rounded figures, so paid + outstanding always equals billed on the page. */
        private BigDecimal outstanding() {
            return rounded(billed).subtract(rounded(paid));
        }

        private Row toRow() {
            return new Row(key, label, billCount, rounded(rent), rounded(water), rounded(electric),
                rounded(commonFee), rounded(billed), rounded(paid), outstanding());
        }

        private Totals toTotals() {
            return new Totals(billCount, rounded(rent), rounded(water), rounded(electric),
                rounded(commonFee), rounded(billed), rounded(paid), outstanding());
        }
    }
}
