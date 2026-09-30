package com.example.expensetracker.dto.response;

import com.example.expensetracker.entity.Category;
import com.example.expensetracker.enums.TransactionType;

import java.time.LocalDateTime;

public record CategoryResponse(
    Long id,
    Long userId,
    String name,
    TransactionType type,
    String color,
    String icon,
    boolean isGlobal,
    LocalDateTime createdAt
) {
    public static CategoryResponse fromEntity(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getUser() != null ? category.getUser().getId() : null,
            category.getName(),
            category.getType(),
            category.getColor(),
            category.getIcon(),
            category.isGlobal(),
            category.getCreatedAt()
        );
    }
}
