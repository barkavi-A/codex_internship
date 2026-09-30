package com.example.urlshortener.dto.response;

import java.util.List;

public record LinkAnalyticsResponse(
        Long linkId,
        String shortCode,
        long totalClicks,
        long uniqueVisitors,
        long botClicks,
        String granularity,
        List<TimeSeriesPoint> timeSeries
) {}
