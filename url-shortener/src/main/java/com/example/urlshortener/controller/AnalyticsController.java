package com.example.urlshortener.controller;

import com.example.urlshortener.dto.response.BreakdownResponse;
import com.example.urlshortener.dto.response.LinkAnalyticsResponse;
import com.example.urlshortener.dto.response.OverviewAnalyticsResponse;
import com.example.urlshortener.security.UserPrincipal;
import com.example.urlshortener.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Analytics", description = "Endpoints for user and link click analytics and CSV export")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/analytics/overview")
    @Operation(summary = "Get overall account analytics overview")
    public ResponseEntity<OverviewAnalyticsResponse> getOverview(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        OverviewAnalyticsResponse overview = analyticsService.getOverview(userPrincipal.getId(), from, to);
        return ResponseEntity.ok(overview);
    }

    @GetMapping("/links/{id}/analytics")
    @Operation(summary = "Get click time-series analytics for a specific link")
    public ResponseEntity<LinkAnalyticsResponse> getLinkAnalytics(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "day") String granularity) {
        LinkAnalyticsResponse analytics = analyticsService.getLinkAnalytics(userPrincipal.getId(), id, from, to, granularity);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/links/{id}/analytics/breakdown")
    @Operation(summary = "Get dimension breakdown analytics (referrer, browser, os, device, country)")
    public ResponseEntity<BreakdownResponse> getBreakdown(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @RequestParam(defaultValue = "referrer") String dimension,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "10") Integer limit) {
        BreakdownResponse breakdown = analyticsService.getBreakdown(userPrincipal.getId(), id, dimension, from, to, limit);
        return ResponseEntity.ok(breakdown);
    }

    @GetMapping("/analytics/top-links")
    @Operation(summary = "Get top performing short links for the user")
    public ResponseEntity<List<OverviewAnalyticsResponse.TopLinkItem>> getTopLinks(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "10") Integer limit) {
        List<OverviewAnalyticsResponse.TopLinkItem> topLinks = analyticsService.getTopLinks(userPrincipal.getId(), from, to, limit);
        return ResponseEntity.ok(topLinks);
    }

    @GetMapping("/links/{id}/analytics/export")
    @Operation(summary = "Export link click stats to CSV")
    public ResponseEntity<byte[]> exportCsv(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @RequestParam(defaultValue = "csv") String format) {
        byte[] csvData = analyticsService.exportCsv(userPrincipal.getId(), id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "link_" + id + "_analytics.csv");
        headers.setContentLength(csvData.length);

        return ResponseEntity.ok().headers(headers).body(csvData);
    }
}
