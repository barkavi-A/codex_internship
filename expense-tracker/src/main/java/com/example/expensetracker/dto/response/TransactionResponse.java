package com.example.expensetracker.dto.response;

import com.example.expensetracker.entity.Transaction;
import com.example.expensetracker.enums.PaymentMethod;
import com.example.expensetracker.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransactionResponse(
    Long id,
    Long categoryId,
    String categoryName,
    String categoryColor,
    String categoryIcon,
    TransactionType type,
    BigDecimal amount,
    String description,
    LocalDate transactionDate,
    PaymentMethod paymentMethod,
    String notes,
    Long version,
    LocalDateTime createdAt,
    String budgetWarning
) {
    public static TransactionResponse fromEntity(Transaction t, String budgetWarning) {
        return new TransactionResponse(
            t.getId(),
            t.getCategory().getId(),
            t.getCategory().getName(),
            t.getCategory().getColor(),
            t.getCategory().getIcon(),
            t.getType(),
            t.getAmount(),
            t.getDescription(),
            t.getTransactionDate(),
            t.getPaymentMethod(),
            t.getNotes(),
            t.getVersion(),
            t.getCreatedAt(),
            budgetWarning
        );
    }

    public static TransactionResponse fromEntity(Transaction t) {
        return fromEntity(t, null);
    }
}
