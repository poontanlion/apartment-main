package com.victory.apartment.controller;

import com.victory.apartment.service.PriceRuleException;
import com.victory.apartment.service.ReportService;
import com.victory.apartment.service.ReportService.Filter;
import com.victory.apartment.service.ReportService.GroupBy;
import com.victory.apartment.service.ReportService.Summary;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Set;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final Set<String> STATUSES = Set.of("All", "Paid", "Pending");

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/summary")
    public Summary summary(@RequestParam(required = false) String groupBy,
                           @RequestParam(required = false) String from,
                           @RequestParam(required = false) String to,
                           @RequestParam(required = false) String buildingId,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false) String search) {
        GroupBy grouping = parseGroupBy(groupBy);
        YearMonth fromMonth = parseMonth(from);
        YearMonth toMonth = parseMonth(to);
        if (fromMonth != null && toMonth != null && fromMonth.isAfter(toMonth)) {
            throw badRequest("INVALID_MONTH_RANGE", "The start month must not be after the end month.");
        }
        if (status != null && !status.isEmpty() && !STATUSES.contains(status)) {
            throw badRequest("INVALID_STATUS", "Status must be All, Paid or Pending.");
        }
        return reports.summarize(new Filter(grouping, fromMonth, toMonth, buildingId, status, search));
    }

    private static GroupBy parseGroupBy(String groupBy) {
        try {
            return GroupBy.valueOf(String.valueOf(groupBy));
        } catch (IllegalArgumentException e) {
            throw badRequest("INVALID_GROUP_BY", "groupBy must be UNIT, TENANT, MONTH or YEAR.");
        }
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isEmpty()) {
            return null;
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw badRequest("INVALID_MONTH", "Months must look like 2026-08.");
        }
    }

    private static PriceRuleException badRequest(String code, String message) {
        return new PriceRuleException(HttpStatus.BAD_REQUEST, code, message);
    }
}
