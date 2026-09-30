package com.example.expensetracker.dto.response;

import com.example.expensetracker.enums.BudgetStatus;

import java.math.BigDecimal;

public record BudgetStatusResponse(
    Long id,
    Long categoryId,
    String categoryName,
    String month,
    BigDecimal limitAmount,
    BigDecimal spentAmount,
    BigDecimal remainingAmount,
    BigDecimal percentUsed,
    BudgetStatus status
) {}
