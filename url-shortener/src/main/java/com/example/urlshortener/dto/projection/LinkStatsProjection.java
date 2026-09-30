package com.example.urlshortener.dto.projection;

public interface LinkStatsProjection {
    Long getTotalClicks();
    Long getUniqueVisitors();
    Long getBotClicks();
}
