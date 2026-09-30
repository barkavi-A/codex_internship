package com.example.expensetracker.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyExpenseResponse(
    LocalDate date,
    BigDecimal amount
) {}
