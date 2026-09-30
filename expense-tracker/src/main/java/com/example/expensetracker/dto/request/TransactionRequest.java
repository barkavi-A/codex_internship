package com.example.expensetracker.dto.request;

import com.example.expensetracker.enums.PaymentMethod;
import com.example.expensetracker.enums.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
    @NotNull(message = "Category ID is required")
    Long categoryId,

    @NotNull(message = "Transaction type is required")
    TransactionType type,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 13, fraction = 2, message = "Amount cannot have more than 2 decimal places")
    BigDecimal amount,

    @NotBlank(message = "Description is required")
    @Size(max = 255, message = "Description must not exceed 255 characters")
    String description,

    @NotNull(message = "Transaction date is required")
    LocalDate transactionDate,

    @NotNull(message = "Payment method is required")
    PaymentMethod paymentMethod,

    String notes
) {}
