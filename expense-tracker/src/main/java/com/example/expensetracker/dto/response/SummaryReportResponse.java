package com.example.expensetracker.dto.response;

import java.math.BigDecimal;

public record SummaryReportResponse(
    BigDecimal totalIncome,
    BigDecimal totalExpense,
    BigDecimal netSavings,
    BigDecimal savingsRate,
    long transactionCount,
    BigDecimal averageDailyExpense,
    BigDecimal highestExpense
) {}
