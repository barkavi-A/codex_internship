package com.example.expensetracker.dto.response;

import java.math.BigDecimal;

public record CategoryReportResponse(
    Long categoryId,
    String categoryName,
    String categoryColor,
    String categoryIcon,
    BigDecimal amount,
    BigDecimal percentage
) {}
