package com.example.expensetracker.dto.response;

import com.example.expensetracker.entity.Budget;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BudgetResponse(
    Long id,
    Long categoryId,
    String categoryName,
    String month,
    BigDecimal limitAmount,
    boolean isOverall,
    LocalDateTime createdAt
) {
    public static BudgetResponse fromEntity(Budget b) {
        return new BudgetResponse(
            b.getId(),
            b.getCategory() != null ? b.getCategory().getId() : null,
            b.getCategory() != null ? b.getCategory().getName() : "Overall Budget",
            b.getMonth(),
            b.getLimitAmount(),
            b.isOverallBudget(),
            b.getCreatedAt()
        );
    }
}
