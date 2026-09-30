package com.example.urlshortener.dto.response;

public record TimeSeriesPoint(
        String bucketKey,
        long clicks,
        long uniqueVisitors
) {}
