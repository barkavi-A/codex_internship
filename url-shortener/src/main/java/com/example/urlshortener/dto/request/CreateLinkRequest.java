package com.example.urlshortener.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateLinkRequest(
        @NotBlank(message = "URL is required")
        @Size(max = 2048, message = "URL must not exceed 2048 characters")
        String url,

        @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$", message = "Custom alias must be 3-32 characters long and contain only letters, numbers, underscores, or hyphens")
        String customAlias,

        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        LocalDateTime expiresAt,

        Long maxClicks,

        Boolean forceNew
) {}
