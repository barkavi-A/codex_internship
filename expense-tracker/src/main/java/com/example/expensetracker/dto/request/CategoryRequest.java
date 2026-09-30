package com.example.expensetracker.dto.request;

import com.example.expensetracker.enums.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must not exceed 100 characters")
    String name,

    @NotNull(message = "Category type is required")
    TransactionType type,

    @Size(max = 20, message = "Color code must not exceed 20 characters")
    String color,

    @Size(max = 50, message = "Icon name must not exceed 50 characters")
    String icon
) {}
