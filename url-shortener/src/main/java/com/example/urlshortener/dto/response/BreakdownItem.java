package com.example.urlshortener.dto.response;

public record BreakdownItem(
        String name,
        long count,
        double percentage
) {}
