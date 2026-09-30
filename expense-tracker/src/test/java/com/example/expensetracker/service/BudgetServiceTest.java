package com.example.expensetracker.service;

import com.example.expensetracker.dto.request.BudgetRequest;
import com.example.expensetracker.dto.response.BudgetResponse;
import com.example.expensetracker.dto.response.BudgetStatusResponse;
import com.example.expensetracker.entity.Budget;
import com.example.expensetracker.entity.Category;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.enums.BudgetStatus;
import com.example.expensetracker.enums.TransactionType;
import com.example.expensetracker.exception.BadRequestException;
import com.example.expensetracker.exception.ConflictException;
import com.example.expensetracker.exception.ResourceNotFoundException;
import com.example.expensetracker.repository.BudgetRepository;
import com.example.expensetracker.repository.CategoryRepository;
import com.example.expensetracker.repository.TransactionRepository;
import com.example.expensetracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private BudgetService budgetService;

    private User user;
    private Category expenseCat;
    private Category incomeCat;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(budgetRepository, categoryRepository, userRepository, transactionRepository);
        user = new User(1L, "Bob", "bob@example.com", "hash", "INR");
        expenseCat = new Category(10L, user, "Food", TransactionType.EXPENSE, "#FF0000", "food");
        incomeCat = new Category(20L, user, "Salary", TransactionType.INCOME, "#00FF00", "wallet");
    }

    @Test
    @DisplayName("Should successfully create a category budget")
    void testCreateCategoryBudget() {
        BudgetRequest request = new BudgetRequest(10L, "2026-09", BigDecimal.valueOf(5000.00));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(categoryRepository.findAccessibleById(10L, 1L)).thenReturn(Optional.of(expenseCat));
        when(budgetRepository.existsByUserIdAndCategoryIdAndMonth(1L, 10L, "2026-09")).thenReturn(false);
        when(budgetRepository.save(any(Budget.class))).thenAnswer(i -> {
            Budget b = i.getArgument(0);
            b.setId(201L);
            return b;
        });

        BudgetResponse response = budgetService.createBudget(1L, request);
        assertNotNull(response);
        assertEquals(201L, response.id());
        assertEquals("Food", response.categoryName());
        assertFalse(response.isOverall());
    }

    @Test
    @DisplayName("Should successfully create an overall monthly budget")
    void testCreateOverallBudget() {
        BudgetRequest request = new BudgetRequest(null, "2026-09", BigDecimal.valueOf(50000.00));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(budgetRepository.existsByUserIdAndCategoryIsNullAndMonth(1L, "2026-09")).thenReturn(false);
        when(budgetRepository.save(any(Budget.class))).thenAnswer(i -> {
            Budget b = i.getArgument(0);
            b.setId(202L);
            return b;
        });

        BudgetResponse response = budgetService.createBudget(1L, request);
        assertNotNull(response);
        assertEquals(202L, response.id());
        assertTrue(response.isOverall());
    }

    @Test
    @DisplayName("Should throw ConflictException on duplicate budget for same category and month")
    void testDuplicateBudgetThrowsConflict() {
        BudgetRequest request = new BudgetRequest(10L, "2026-09", BigDecimal.valueOf(5000.00));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(categoryRepository.findAccessibleById(10L, 1L)).thenReturn(Optional.of(expenseCat));
        when(budgetRepository.existsByUserIdAndCategoryIdAndMonth(1L, 10L, "2026-09")).thenReturn(true);

        assertThrows(ConflictException.class, () -> budgetService.createBudget(1L, request));
    }

    @Test
    @DisplayName("Should throw BadRequestException when trying to budget an INCOME category")
    void testIncomeCategoryBudgetThrowsBadRequest() {
        BudgetRequest request = new BudgetRequest(20L, "2026-09", BigDecimal.valueOf(5000.00));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(categoryRepository.findAccessibleById(20L, 1L)).thenReturn(Optional.of(incomeCat));

        assertThrows(BadRequestException.class, () -> budgetService.createBudget(1L, request));
    }

    @Test
    @DisplayName("Should accurately calculate OK, WARNING, and EXCEEDED budget statuses")
    void testGetBudgetStatusCalculations() {
        // Budget 1: Limit 1000, Spent 500 (50%) -> OK
        Budget b1 = new Budget(1L, user, expenseCat, "2026-09", BigDecimal.valueOf(1000.00));
        // Budget 2: Limit 1000, Spent 850 (85%) -> WARNING
        Category entCat = new Category(11L, user, "Entertainment", TransactionType.EXPENSE, "#000", "film");
        Budget b2 = new Budget(2L, user, entCat, "2026-09", BigDecimal.valueOf(1000.00));
        // Budget 3: Overall Limit 10000, Spent 12000 (120%) -> EXCEEDED
        Budget b3 = new Budget(3L, user, null, "2026-09", BigDecimal.valueOf(10000.00));

        when(budgetRepository.findByUserIdAndMonth(1L, "2026-09")).thenReturn(List.of(b1, b2, b3));

        when(transactionRepository.sumCategoryExpensesInDateRange(eq(1L), eq(10L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(BigDecimal.valueOf(500.00));
        when(transactionRepository.sumCategoryExpensesInDateRange(eq(1L), eq(11L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(BigDecimal.valueOf(850.00));
        when(transactionRepository.sumOverallExpensesInDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(BigDecimal.valueOf(12000.00));

        List<BudgetStatusResponse> statuses = budgetService.getBudgetStatus(1L, "2026-09");
        assertEquals(3, statuses.size());

        // Assert B1
        assertEquals(BudgetStatus.OK, statuses.get(0).status());
        assertEquals(BigDecimal.valueOf(50.0).setScale(2), statuses.get(0).percentUsed());
        assertEquals(BigDecimal.valueOf(500.00), statuses.get(0).remainingAmount());

        // Assert B2
        assertEquals(BudgetStatus.WARNING, statuses.get(1).status());
        assertEquals(BigDecimal.valueOf(85.0).setScale(2), statuses.get(1).percentUsed());
        assertEquals(BigDecimal.valueOf(150.00), statuses.get(1).remainingAmount());

        // Assert B3
        assertEquals(BudgetStatus.EXCEEDED, statuses.get(2).status());
        assertEquals(BigDecimal.valueOf(120.0).setScale(2), statuses.get(2).percentUsed());
        assertEquals(BigDecimal.valueOf(-2000.00), statuses.get(2).remainingAmount());
    }

    @Test
    @DisplayName("Should delete budget successfully or throw 404 when not found")
    void testDeleteBudget() {
        Budget b = new Budget(10L, user, expenseCat, "2026-09", BigDecimal.valueOf(1000.00));
        when(budgetRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(b));

        budgetService.deleteBudget(1L, 10L);
        verify(budgetRepository, times(1)).delete(b);

        when(budgetRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> budgetService.deleteBudget(1L, 999L));
    }
}
