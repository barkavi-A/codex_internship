package com.example.expensetracker.service;

import com.example.expensetracker.dto.response.*;
import com.example.expensetracker.entity.Transaction;
import com.example.expensetracker.enums.TransactionType;
import com.example.expensetracker.repository.TransactionRepository;
import com.example.expensetracker.util.DateRangeUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service providing database-side aggregated financial reports and analytics.
 */
@Service
public class ReportService {

    private final TransactionRepository transactionRepository;

    public ReportService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Computes the overall financial summary (income, expense, savings rate, averages) for a date range.
     *
     * @param userId ID of the authenticated user
     * @param from optional start date (defaults to first of current month)
     * @param to optional end date (defaults to end of current month)
     * @return SummaryReportResponse DTO
     */
    @Transactional(readOnly = true)
    public SummaryReportResponse getSummary(Long userId, LocalDate from, LocalDate to) {
        DateRangeUtil.DateRange range = DateRangeUtil.resolveAndValidate(from, to);

        BigDecimal totalIncome = transactionRepository.sumByUserAndTypeAndDateBetween(
            userId, TransactionType.INCOME, range.from(), range.to()
        );
        BigDecimal totalExpense = transactionRepository.sumByUserAndTypeAndDateBetween(
            userId, TransactionType.EXPENSE, range.from(), range.to()
        );

        BigDecimal netSavings = totalIncome.subtract(totalExpense);
        BigDecimal savingsRate = BigDecimal.ZERO;
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = netSavings.multiply(BigDecimal.valueOf(100))
                .divide(totalIncome, 2, RoundingMode.HALF_UP);
        }

        long count = transactionRepository.countByUserAndDateBetween(userId, range.from(), range.to());
        BigDecimal highestExpense = transactionRepository.highestExpenseByUserAndDateBetween(userId, range.from(), range.to());

        long days = ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
        BigDecimal averageDailyExpense = totalExpense.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);

        return new SummaryReportResponse(
            totalIncome.setScale(2, RoundingMode.HALF_UP),
            totalExpense.setScale(2, RoundingMode.HALF_UP),
            netSavings.setScale(2, RoundingMode.HALF_UP),
            savingsRate,
            count,
            averageDailyExpense,
            highestExpense.setScale(2, RoundingMode.HALF_UP)
        );
    }

    /**
     * Aggregates transactions by category for a date range and calculates percentage contribution.
     *
     * @param userId ID of the authenticated user
     * @param from optional start date
     * @param to optional end date
     * @param type transaction type (defaults to EXPENSE)
     * @return List of CategoryReportResponse DTOs sorted descending by amount
     */
    @Transactional(readOnly = true)
    public List<CategoryReportResponse> getCategoryReport(Long userId, LocalDate from, LocalDate to, TransactionType type) {
        DateRangeUtil.DateRange range = DateRangeUtil.resolveAndValidate(from, to);
        TransactionType targetType = type != null ? type : TransactionType.EXPENSE;

        List<Object[]> rows = transactionRepository.aggregateByCategory(userId, targetType, range.from(), range.to());
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (Object[] row : rows) {
            BigDecimal amount = (BigDecimal) row[4];
            grandTotal = grandTotal.add(amount);
        }

        List<CategoryReportResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            Long categoryId = (Long) row[0];
            String name = (String) row[1];
            String color = (String) row[2];
            String icon = (String) row[3];
            BigDecimal amount = ((BigDecimal) row[4]).setScale(2, RoundingMode.HALF_UP);

            BigDecimal percentage = BigDecimal.ZERO;
            if (grandTotal.compareTo(BigDecimal.ZERO) > 0) {
                percentage = amount.multiply(BigDecimal.valueOf(100)).divide(grandTotal, 2, RoundingMode.HALF_UP);
            }

            result.add(new CategoryReportResponse(categoryId, name, color, icon, amount, percentage));
        }

        return result;
    }

    /**
     * Returns 12-month breakdown of income, expense, and net savings for a given year.
     *
     * @param userId ID of the authenticated user
     * @param year target calendar year
     * @return 12 monthly report entries
     */
    @Transactional(readOnly = true)
    public List<MonthlyReportResponse> getMonthlyReport(Long userId, int year) {
        List<Object[]> rows = transactionRepository.aggregateMonthlyForYear(userId, year);

        Map<Integer, BigDecimal> incomeMap = new HashMap<>();
        Map<Integer, BigDecimal> expenseMap = new HashMap<>();

        for (Object[] row : rows) {
            int monthNum = ((Number) row[0]).intValue();
            TransactionType type = (TransactionType) row[1];
            BigDecimal total = (BigDecimal) row[2];

            if (type == TransactionType.INCOME) {
                incomeMap.put(monthNum, total);
            } else {
                expenseMap.put(monthNum, total);
            }
        }

        List<MonthlyReportResponse> monthlyReports = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            String monthStr = String.format("%04d-%02d", year, m);
            BigDecimal income = incomeMap.getOrDefault(m, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal expense = expenseMap.getOrDefault(m, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = income.subtract(expense).setScale(2, RoundingMode.HALF_UP);

            monthlyReports.add(new MonthlyReportResponse(monthStr, income, expense, net));
        }

        return monthlyReports;
    }

    /**
     * Returns daily expenses for charting.
     *
     * @param userId ID of the authenticated user
     * @param from optional start date
     * @param to optional end date
     * @return List of DailyExpenseResponse
     */
    @Transactional(readOnly = true)
    public List<DailyExpenseResponse> getDailyExpenseReport(Long userId, LocalDate from, LocalDate to) {
        DateRangeUtil.DateRange range = DateRangeUtil.resolveAndValidate(from, to);
        List<Object[]> rows = transactionRepository.aggregateDailyExpenses(userId, range.from(), range.to());

        List<DailyExpenseResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate date = (LocalDate) row[0];
            BigDecimal amount = ((BigDecimal) row[1]).setScale(2, RoundingMode.HALF_UP);
            result.add(new DailyExpenseResponse(date, amount));
        }
        return result;
    }

    /**
     * Returns top expense transactions for a date range.
     *
     * @param userId ID of the authenticated user
     * @param from optional start date
     * @param to optional end date
     * @param limit maximum transactions to return (default 10)
     * @return List of TransactionResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTopExpenses(Long userId, LocalDate from, LocalDate to, int limit) {
        DateRangeUtil.DateRange range = DateRangeUtil.resolveAndValidate(from, to);
        int effectiveLimit = (limit <= 0 || limit > 100) ? 10 : limit;

        List<Transaction> topList = transactionRepository.findTopExpenses(
            userId, range.from(), range.to(), PageRequest.of(0, effectiveLimit)
        );

        return topList.stream().map(TransactionResponse::fromEntity).toList();
    }
}
