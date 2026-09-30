package com.example.urlshortener.dto.response;

import java.util.List;

public record BreakdownResponse(
        String dimension,
        long totalCount,
        List<BreakdownItem> items
) {}
