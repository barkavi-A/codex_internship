package com.example.expensetracker.controller;

import com.example.expensetracker.dto.response.*;
import com.example.expensetracker.enums.TransactionType;
import com.example.expensetracker.security.UserPrincipal;
import com.example.expensetracker.service.CsvExportService;
import com.example.expensetracker.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Endpoints for aggregated analytics, financial metrics, and data export")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;
    private final CsvExportService csvExportService;

    public ReportController(ReportService reportService, CsvExportService csvExportService) {
        this.reportService = reportService;
        this.csvExportService = csvExportService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get high-level financial summary metrics for a date range")
    public ResponseEntity<SummaryReportResponse> getSummary(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        log.info("Generating summary report for user id: {}", userPrincipal.getId());
        SummaryReportResponse response = reportService.getSummary(userPrincipal.getId(), from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-category")
    @Operation(summary = "Get spending breakdown by category with percentages")
    public ResponseEntity<List<CategoryReportResponse>> getByCategory(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "EXPENSE") TransactionType type
    ) {
        log.info("Generating category report for user id: {}, type: {}", userPrincipal.getId(), type);
        List<CategoryReportResponse> responses = reportService.getCategoryReport(userPrincipal.getId(), from, to, type);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/monthly")
    @Operation(summary = "Get 12-month financial trend for a calendar year")
    public ResponseEntity<List<MonthlyReportResponse>> getMonthly(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @RequestParam(required = false) Integer year
    ) {
        int targetYear = (year != null) ? year : LocalDate.now().getYear();
        log.info("Generating monthly trend report for user id: {}, year: {}", userPrincipal.getId(), targetYear);
        List<MonthlyReportResponse> responses = reportService.getMonthlyReport(userPrincipal.getId(), targetYear);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/daily")
    @Operation(summary = "Get daily expense totals for charting")
    public ResponseEntity<List<DailyExpenseResponse>> getDaily(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        log.info("Generating daily expense report for user id: {}", userPrincipal.getId());
        List<DailyExpenseResponse> responses = reportService.getDailyExpenseReport(userPrincipal.getId(), from, to);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/top-expenses")
    @Operation(summary = "Get top expense transactions for a date range")
    public ResponseEntity<List<TransactionResponse>> getTopExpenses(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "10") int limit
    ) {
        log.info("Fetching top expenses for user id: {}, limit: {}", userPrincipal.getId(), limit);
        List<TransactionResponse> responses = reportService.getTopExpenses(userPrincipal.getId(), from, to, limit);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/export")
    @Operation(summary = "Export transactions as CSV file")
    public ResponseEntity<byte[]> exportCsv(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "csv") String format
    ) {
        log.info("Exporting transactions CSV for user id: {}", userPrincipal.getId());
        byte[] csvData = csvExportService.exportTransactionsCsv(userPrincipal.getId(), from, to);

        String filename = "transactions_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
            .body(csvData);
    }
}
