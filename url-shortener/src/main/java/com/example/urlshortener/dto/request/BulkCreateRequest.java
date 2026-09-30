package com.example.urlshortener.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkCreateRequest(
        @NotEmpty(message = "Link list cannot be empty")
        @Size(max = 50, message = "Bulk creation is limited to 50 links per request")
        List<@Valid CreateLinkRequest> links
) {}
