package com.example.expensetracker.dto.response;

import java.math.BigDecimal;

public record MonthlyReportResponse(
    String month,
    BigDecimal income,
    BigDecimal expense,
    BigDecimal net
) {}
