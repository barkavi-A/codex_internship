package com.example.expensetracker.service;

import com.example.expensetracker.dto.response.*;
import com.example.expensetracker.entity.Category;
import com.example.expensetracker.entity.Transaction;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.enums.PaymentMethod;
import com.example.expensetracker.enums.TransactionType;
import com.example.expensetracker.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(transactionRepository);
    }

    @Test
    @DisplayName("Should accurately calculate summary metrics with hand-calculated values")
    void testGetSummaryHandCalculated() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30); // 30 days

        when(transactionRepository.sumByUserAndTypeAndDateBetween(1L, TransactionType.INCOME, from, to))
            .thenReturn(BigDecimal.valueOf(80000.00));
        when(transactionRepository.sumByUserAndTypeAndDateBetween(1L, TransactionType.EXPENSE, from, to))
            .thenReturn(BigDecimal.valueOf(50000.00));
        when(transactionRepository.countByUserAndDateBetween(1L, from, to))
            .thenReturn(25L);
        when(transactionRepository.highestExpenseByUserAndDateBetween(1L, from, to))
            .thenReturn(BigDecimal.valueOf(20000.00));

        SummaryReportResponse summary = reportService.getSummary(1L, from, to);

        // Hand-calculated expected values:
        // Total Income: 80,000.00
        // Total Expense: 50,000.00
        // Net Savings: 30,000.00
        // Savings Rate: (30,000 / 80,000) * 100 = 37.50%
        // Average Daily: 50,000 / 30 = 1666.67
        assertEquals(BigDecimal.valueOf(80000.00).setScale(2, RoundingMode.HALF_UP), summary.totalIncome());
        assertEquals(BigDecimal.valueOf(50000.00).setScale(2, RoundingMode.HALF_UP), summary.totalExpense());
        assertEquals(BigDecimal.valueOf(30000.00).setScale(2, RoundingMode.HALF_UP), summary.netSavings());
        assertEquals(BigDecimal.valueOf(37.50).setScale(2, RoundingMode.HALF_UP), summary.savingsRate());
        assertEquals(25L, summary.transactionCount());
        assertEquals(BigDecimal.valueOf(1666.67), summary.averageDailyExpense());
        assertEquals(BigDecimal.valueOf(20000.00).setScale(2, RoundingMode.HALF_UP), summary.highestExpense());
    }

    @Test
    @DisplayName("Should return 0% savings rate when total income is zero")
    void testGetSummaryZeroIncome() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 10); // 10 days

        when(transactionRepository.sumByUserAndTypeAndDateBetween(1L, TransactionType.INCOME, from, to))
            .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumByUserAndTypeAndDateBetween(1L, TransactionType.EXPENSE, from, to))
            .thenReturn(BigDecimal.valueOf(1000.00));
        when(transactionRepository.countByUserAndDateBetween(1L, from, to))
            .thenReturn(2L);
        when(transactionRepository.highestExpenseByUserAndDateBetween(1L, from, to))
            .thenReturn(BigDecimal.valueOf(600.00));

        SummaryReportResponse summary = reportService.getSummary(1L, from, to);

        assertEquals(BigDecimal.ZERO.setScale(2), summary.totalIncome());
        assertEquals(BigDecimal.valueOf(-1000.00).setScale(2), summary.netSavings());
        assertEquals(BigDecimal.ZERO, summary.savingsRate());
        assertEquals(BigDecimal.valueOf(100.00).setScale(2), summary.averageDailyExpense());
    }

    @Test
    @DisplayName("Should calculate category distribution and percentages accurately")
    void testGetCategoryReport() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        // Category 1: 600.00, Category 2: 400.00 -> Total = 1000.00 -> 60.00% and 40.00%
        Object[] row1 = new Object[]{1L, "Food", "#FF0000", "utensils", BigDecimal.valueOf(600.00)};
        Object[] row2 = new Object[]{2L, "Travel", "#00FF00", "plane", BigDecimal.valueOf(400.00)};

        when(transactionRepository.aggregateByCategory(1L, TransactionType.EXPENSE, from, to))
            .thenReturn(List.of(row1, row2));

        List<CategoryReportResponse> report = reportService.getCategoryReport(1L, from, to, TransactionType.EXPENSE);

        assertEquals(2, report.size());
        assertEquals("Food", report.get(0).categoryName());
        assertEquals(BigDecimal.valueOf(60.00).setScale(2), report.get(0).percentage());
        assertEquals("Travel", report.get(1).categoryName());
        assertEquals(BigDecimal.valueOf(40.00).setScale(2), report.get(1).percentage());
    }

    @Test
    @DisplayName("Should return 12 months with zeros for months without data")
    void testGetMonthlyReport12Months() {
        // Month 3: income 10000, Month 3: expense 4000
        Object[] m3Income = new Object[]{3, TransactionType.INCOME, BigDecimal.valueOf(10000.00)};
        Object[] m3Expense = new Object[]{3, TransactionType.EXPENSE, BigDecimal.valueOf(4000.00)};

        when(transactionRepository.aggregateMonthlyForYear(1L, 2026))
            .thenReturn(List.of(m3Income, m3Expense));

        List<MonthlyReportResponse> report = reportService.getMonthlyReport(1L, 2026);

        assertEquals(12, report.size());

        // Month 1 (no data)
        assertEquals("2026-01", report.get(0).month());
        assertEquals(BigDecimal.ZERO.setScale(2), report.get(0).income());
        assertEquals(BigDecimal.ZERO.setScale(2), report.get(0).expense());
        assertEquals(BigDecimal.ZERO.setScale(2), report.get(0).net());

        // Month 3 (with data)
        assertEquals("2026-03", report.get(2).month());
        assertEquals(BigDecimal.valueOf(10000.00).setScale(2), report.get(2).income());
        assertEquals(BigDecimal.valueOf(4000.00).setScale(2), report.get(2).expense());
        assertEquals(BigDecimal.valueOf(6000.00).setScale(2), report.get(2).net());
    }

    @Test
    @DisplayName("Should return daily expenses and top expenses")
    void testGetDailyAndTopExpenses() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(new Object[]{LocalDate.of(2026, 9, 5), BigDecimal.valueOf(250.00)});
        when(transactionRepository.aggregateDailyExpenses(1L, from, to)).thenReturn(rows);

        List<DailyExpenseResponse> daily = reportService.getDailyExpenseReport(1L, from, to);
        assertEquals(1, daily.size());
        assertEquals(LocalDate.of(2026, 9, 5), daily.get(0).date());
        assertEquals(BigDecimal.valueOf(250.00).setScale(2), daily.get(0).amount());

        User user = new User(1L, "Bob", "bob@example.com", "hash", "INR");
        Category cat = new Category(1L, user, "Food", TransactionType.EXPENSE, "#000", "food");
        Transaction topTx = new Transaction(99L, user, cat, TransactionType.EXPENSE, BigDecimal.valueOf(5000.00), "Top", from, PaymentMethod.CARD, null);

        when(transactionRepository.findTopExpenses(eq(1L), eq(from), eq(to), any(Pageable.class)))
            .thenReturn(List.of(topTx));

        List<TransactionResponse> topList = reportService.getTopExpenses(1L, from, to, 5);
        assertEquals(1, topList.size());
        assertEquals(99L, topList.get(0).id());
        assertEquals(BigDecimal.valueOf(5000.00), topList.get(0).amount());
    }
}
