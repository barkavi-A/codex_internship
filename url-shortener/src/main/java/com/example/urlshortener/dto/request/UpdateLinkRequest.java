package com.example.urlshortener.dto.request;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record UpdateLinkRequest(
        @Size(max = 2048, message = "URL must not exceed 2048 characters")
        String originalUrl,

        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        LocalDateTime expiresAt,

        Long maxClicks,

        Boolean active
) {}
