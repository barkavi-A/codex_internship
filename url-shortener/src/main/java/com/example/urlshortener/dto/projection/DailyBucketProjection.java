package com.example.urlshortener.dto.projection;

public interface DailyBucketProjection {
    String getBucketKey();
    Long getClicks();
    Long getUniqueVisitors();
}
