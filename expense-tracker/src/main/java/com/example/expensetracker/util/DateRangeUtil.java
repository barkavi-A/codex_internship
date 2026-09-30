package com.example.expensetracker.util;

import com.example.expensetracker.exception.BadRequestException;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public class DateRangeUtil {

    public record DateRange(LocalDate from, LocalDate to) {}

    public static DateRange resolveAndValidate(LocalDate from, LocalDate to) {
        LocalDate resolvedFrom = from;
        LocalDate resolvedTo = to;

        if (resolvedFrom == null && resolvedTo == null) {
            LocalDate now = LocalDate.now();
            resolvedFrom = now.with(TemporalAdjusters.firstDayOfMonth());
            resolvedTo = now.with(TemporalAdjusters.lastDayOfMonth());
        } else if (resolvedFrom == null) {
            resolvedFrom = resolvedTo.with(TemporalAdjusters.firstDayOfMonth());
        } else if (resolvedTo == null) {
            resolvedTo = resolvedFrom.with(TemporalAdjusters.lastDayOfMonth());
        }

        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new BadRequestException("Start date ('from') must be on or before end date ('to')");
        }

        if (resolvedFrom.plusYears(5).isBefore(resolvedTo)) {
            throw new BadRequestException("Date range cannot exceed 5 years");
        }

        return new DateRange(resolvedFrom, resolvedTo);
    }
}
