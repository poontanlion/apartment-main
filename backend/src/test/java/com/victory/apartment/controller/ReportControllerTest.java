package com.victory.apartment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.victory.apartment.service.ReportService;
import com.victory.apartment.service.ReportService.Filter;
import com.victory.apartment.service.ReportService.GroupBy;
import com.victory.apartment.service.ReportService.Row;
import com.victory.apartment.service.ReportService.Summary;
import com.victory.apartment.service.ReportService.Totals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

    private static final String URL = "/api/reports/summary";

    @Autowired private MockMvc mockMvc;
    @MockBean private ReportService reportService;

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static Summary summary() {
        Totals totals = new Totals(2, money("10000.00"), money("180.00"), money("800.00"),
            money("600.00"), money("11580.00"), money("5790.00"), money("5790.00"));
        Row row = new Row("rm-A101", "A101", 2, money("10000.00"), money("180.00"), money("800.00"),
            money("600.00"), money("11580.00"), money("5790.00"), money("5790.00"));
        return new Summary(GroupBy.UNIT, totals, List.of(row), 3, 1);
    }

    @Test
    void summary_returnsReportShape() throws Exception {
        when(reportService.summarize(Filter.of(GroupBy.UNIT))).thenReturn(summary());

        mockMvc.perform(get(URL).param("groupBy", "UNIT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groupBy").value("UNIT"))
            .andExpect(jsonPath("$.totals.billCount").value(2))
            .andExpect(jsonPath("$.totals.billed").value(11580.00))
            .andExpect(jsonPath("$.totals.paid").value(5790.00))
            .andExpect(jsonPath("$.totals.outstanding").value(5790.00))
            .andExpect(jsonPath("$.rows[0].key").value("rm-A101"))
            .andExpect(jsonPath("$.rows[0].label").value("A101"))
            .andExpect(jsonPath("$.rows[0].rent").value(10000.00))
            .andExpect(jsonPath("$.rows[0].water").value(180.00))
            .andExpect(jsonPath("$.rows[0].electric").value(800.00))
            .andExpect(jsonPath("$.rows[0].commonFee").value(600.00))
            .andExpect(jsonPath("$.skippedBills").value(3))
            .andExpect(jsonPath("$.duplicateRoomMonths").value(1));
    }

    @Test
    void summary_passesEveryFilter() throws Exception {
        Filter expected = new Filter(GroupBy.TENANT, YearMonth.of(2026, 1), YearMonth.of(2026, 12), "bld-1", "Paid", "somchai");
        when(reportService.summarize(expected)).thenReturn(summary());

        mockMvc.perform(get(URL)
                .param("groupBy", "TENANT")
                .param("from", "2026-01")
                .param("to", "2026-12")
                .param("buildingId", "bld-1")
                .param("status", "Paid")
                .param("search", "somchai"))
            .andExpect(status().isOk());

        verify(reportService).summarize(expected);
    }

    @Test
    void summary_blankMonthsMeanNoLimit() throws Exception {
        Filter expected = new Filter(GroupBy.YEAR, null, null, null, null, null);
        when(reportService.summarize(expected)).thenReturn(summary());

        mockMvc.perform(get(URL).param("groupBy", "YEAR").param("from", "").param("to", ""))
            .andExpect(status().isOk());

        verify(reportService).summarize(expected);
    }

    @Test
    void summary_sameMonthRangeIsAllowed() throws Exception {
        when(reportService.summarize(any())).thenReturn(summary());

        mockMvc.perform(get(URL).param("groupBy", "UNIT").param("from", "2026-08").param("to", "2026-08"))
            .andExpect(status().isOk());
    }

    @Test
    void summary_acceptsUnpaidAndAllStatus() throws Exception {
        when(reportService.summarize(any())).thenReturn(summary());

        mockMvc.perform(get(URL).param("groupBy", "UNIT").param("status", "Unpaid")).andExpect(status().isOk());
        mockMvc.perform(get(URL).param("groupBy", "UNIT").param("status", "All")).andExpect(status().isOk());
    }

    @Test
    void summary_missingGroupBy_isRejected() throws Exception {
        expectBadRequest("INVALID_GROUP_BY", null, null, null, null);
    }

    @Test
    void summary_unknownGroupBy_isRejected() throws Exception {
        expectBadRequest("INVALID_GROUP_BY", "FLOOR", null, null, null);
    }

    @Test
    void summary_badFrom_isRejected() throws Exception {
        expectBadRequest("INVALID_MONTH", "UNIT", "August 2026", null, null);
    }

    @Test
    void summary_badTo_isRejected() throws Exception {
        expectBadRequest("INVALID_MONTH", "UNIT", null, "2026-13", null);
    }

    @Test
    void summary_fromAfterTo_isRejected() throws Exception {
        expectBadRequest("INVALID_MONTH_RANGE", "UNIT", "2026-02", "2026-01", null);
    }

    @Test
    void summary_unknownStatus_isRejected() throws Exception {
        expectBadRequest("INVALID_STATUS", "UNIT", null, null, "Overdue");
    }

    private void expectBadRequest(String code, String groupBy, String from, String to, String statusValue) throws Exception {
        var request = get(URL);
        if (groupBy != null) request.param("groupBy", groupBy);
        if (from != null) request.param("from", from);
        if (to != null) request.param("to", to);
        if (statusValue != null) request.param("status", statusValue);

        mockMvc.perform(request)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(code));

        verify(reportService, never()).summarize(any());
    }
}
