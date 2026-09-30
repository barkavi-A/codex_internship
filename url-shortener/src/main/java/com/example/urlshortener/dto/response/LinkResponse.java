package com.example.urlshortener.dto.response;

import java.time.LocalDateTime;

public record LinkResponse(
        Long id,
        String shortCode,
        String shortUrl,
        String originalUrl,
        String title,
        boolean customAlias,
        LocalDateTime expiresAt,
        Long maxClicks,
        long clickCount,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
