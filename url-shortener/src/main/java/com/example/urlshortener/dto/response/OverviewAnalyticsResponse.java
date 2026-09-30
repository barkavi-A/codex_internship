package com.example.urlshortener.dto.response;

import java.util.List;

public record OverviewAnalyticsResponse(
        long totalLinks,
        long activeLinks,
        long totalClicks,
        long uniqueVisitors,
        long clicksToday,
        double clickChangePercent,
        List<TopLinkItem> topLinks
) {
    public record TopLinkItem(
            Long linkId,
            String shortCode,
            String title,
            String originalUrl,
            long clicks
    ) {}
}
