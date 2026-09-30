package com.example.expensetracker.service;

import com.example.expensetracker.dto.request.TransactionRequest;
import com.example.expensetracker.dto.response.TransactionResponse;
import com.example.expensetracker.entity.Budget;
import com.example.expensetracker.entity.Category;
import com.example.expensetracker.entity.Transaction;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.enums.PaymentMethod;
import com.example.expensetracker.enums.TransactionType;
import com.example.expensetracker.exception.BadRequestException;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BudgetRepository budgetRepository;

    private TransactionService transactionService;

    private User user;
    private Category expenseCategory;
    private Category incomeCategory;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionRepository, categoryRepository, userRepository, budgetRepository);
        user = new User(1L, "Alice", "alice@example.com", "hash", "INR");
        expenseCategory = new Category(10L, user, "Food", TransactionType.EXPENSE, "#FF0000", "food");
        incomeCategory = new Category(20L, user, "Salary", TransactionType.INCOME, "#00FF00", "wallet");
    }

    @Test
    @DisplayName("Should successfully create a valid transaction")
    void testCreateTransactionSuccess() {
        TransactionRequest request = new TransactionRequest(
            10L, TransactionType.EXPENSE, BigDecimal.valueOf(150.00),
            "Groceries", LocalDate.now(), PaymentMethod.CARD, "Notes"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(categoryRepository.findAccessibleById(10L, 1L)).thenReturn(Optional.of(expenseCategory));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });

        TransactionResponse response = transactionService.createTransaction(1L, request);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("Groceries", response.description());
        assertEquals(BigDecimal.valueOf(150.00).setScale(2), response.amount());
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when category type mismatches transaction type")
    void testCategoryTypeMismatch() {
        // Trying to use Income category with EXPENSE transaction
        TransactionRequest request = new TransactionRequest(
            20L, TransactionType.EXPENSE, BigDecimal.valueOf(100.00),
            "Coffee", LocalDate.now(), PaymentMethod.CASH, null
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(categoryRepository.findAccessibleById(20L, 1L)).thenReturn(Optional.of(incomeCategory));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            transactionService.createTransaction(1L, request)
        );
        assertTrue(ex.getMessage().contains("Category type mismatch"));
    }

    @Test
    @DisplayName("Should throw BadRequestException when transaction date is > 1 year in future")
    void testTransactionDateTooFarInFuture() {
        TransactionRequest request = new TransactionRequest(
            10L, TransactionType.EXPENSE, BigDecimal.valueOf(100.00),
            "Future Event", LocalDate.now().plusYears(2), PaymentMethod.CASH, null
        );

        assertThrows(BadRequestException.class, () ->
            transactionService.createTransaction(1L, request)
        );
    }

    @Test
    @DisplayName("Should trigger 80% budget warning when expense crosses 80% threshold")
    void testBudgetWarningAt80Percent() {
        LocalDate date = LocalDate.of(2026, 9, 15);
        TransactionRequest request = new TransactionRequest(
            10L, TransactionType.EXPENSE, BigDecimal.valueOf(350.00),
            "Dinner", date, PaymentMethod.UPI, null
        );

        Budget budget = new Budget(1L, user, expenseCategory, "2026-09", BigDecimal.valueOf(1000.00));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(categoryRepository.findAccessibleById(10L, 1L)).thenReturn(Optional.of(expenseCategory));
        when(budgetRepository.findByUserIdAndCategoryIdAndMonth(eq(1L), eq(10L), eq("2026-09")))
            .thenReturn(Optional.of(budget));
        // Existing spent is 500, + 350 = 850 (85% of 1000)
        when(transactionRepository.sumCategoryExpensesInDateRange(eq(1L), eq(10L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(BigDecimal.valueOf(500.00));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setId(101L);
            return t;
        });

        TransactionResponse response = transactionService.createTransaction(1L, request);

        assertNotNull(response.budgetWarning());
        assertTrue(response.budgetWarning().contains("85.0% used"));
        assertTrue(response.budgetWarning().contains("Category budget warning"));
    }

    @Test
    @DisplayName("Should trigger 100% budget warning when expense exceeds limit")
    void testBudgetWarningAt100PercentExceeded() {
        LocalDate date = LocalDate.of(2026, 9, 20);
        TransactionRequest request = new TransactionRequest(
            10L, TransactionType.EXPENSE, BigDecimal.valueOf(600.00),
            "Shopping", date, PaymentMethod.CARD, null
        );

        Budget budget = new Budget(1L, user, expenseCategory, "2026-09", BigDecimal.valueOf(1000.00));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(categoryRepository.findAccessibleById(10L, 1L)).thenReturn(Optional.of(expenseCategory));
        when(budgetRepository.findByUserIdAndCategoryIdAndMonth(eq(1L), eq(10L), eq("2026-09")))
            .thenReturn(Optional.of(budget));
        // Existing spent is 500, + 600 = 1100 (110% of 1000)
        when(transactionRepository.sumCategoryExpensesInDateRange(eq(1L), eq(10L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(BigDecimal.valueOf(500.00));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setId(102L);
            return t;
        });

        TransactionResponse response = transactionService.createTransaction(1L, request);

        assertNotNull(response.budgetWarning());
        assertTrue(response.budgetWarning().contains("Category budget exceeded: 110.0% used"));
    }

    @Test
    @DisplayName("Should update transaction successfully")
    void testUpdateTransaction() {
        Transaction existing = new Transaction(
            50L, user, expenseCategory, TransactionType.EXPENSE,
            BigDecimal.valueOf(100.00), "Old Desc", LocalDate.now(), PaymentMethod.CASH, null
        );

        TransactionRequest updateReq = new TransactionRequest(
            10L, TransactionType.EXPENSE, BigDecimal.valueOf(120.00),
            "New Desc", LocalDate.now(), PaymentMethod.CARD, "Updated"
        );

        when(transactionRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findAccessibleById(10L, 1L)).thenReturn(Optional.of(expenseCategory));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(existing);

        TransactionResponse response = transactionService.updateTransaction(1L, 50L, updateReq);
        assertEquals("New Desc", response.description());
        assertEquals(BigDecimal.valueOf(120.00).setScale(2), response.amount());
    }

    @Test
    @DisplayName("Should delete transaction successfully or throw 404 if not found")
    void testDeleteTransaction() {
        Transaction existing = new Transaction(
            50L, user, expenseCategory, TransactionType.EXPENSE,
            BigDecimal.valueOf(100.00), "Desc", LocalDate.now(), PaymentMethod.CASH, null
        );

        when(transactionRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(existing));
        transactionService.deleteTransaction(1L, 50L);
        verify(transactionRepository, times(1)).delete(existing);

        // When not found
        when(transactionRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> transactionService.deleteTransaction(1L, 999L));
    }
}
