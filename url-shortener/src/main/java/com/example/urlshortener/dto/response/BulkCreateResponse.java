package com.example.urlshortener.dto.response;

import java.util.List;

public record BulkCreateResponse(
        List<BulkItemResult> results,
        int total,
        int successCount,
        int failureCount
) {
    public record BulkItemResult(
            int index,
            boolean success,
            LinkResponse link,
            String error
    ) {}
}
