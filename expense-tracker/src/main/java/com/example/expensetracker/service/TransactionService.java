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
import com.example.expensetracker.repository.TransactionSpecification;
import com.example.expensetracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/**
 * Service managing transaction CRUD operations, specifications filtering, and budget warnings.
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;

    public TransactionService(
        TransactionRepository transactionRepository,
        CategoryRepository categoryRepository,
        UserRepository userRepository,
        BudgetRepository budgetRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.budgetRepository = budgetRepository;
    }

    /**
     * Creates a new transaction and checks budget limits for warning alerts.
     *
     * @param userId ID of the authenticated user
     * @param request Transaction creation details
     * @return TransactionResponse DTO with optional budget warning
     */
    @Transactional
    public TransactionResponse createTransaction(Long userId, TransactionRequest request) {
        validateTransactionRequest(request);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Category category = categoryRepository.findAccessibleById(request.categoryId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));

        if (category.getType() != request.type()) {
            throw new BadRequestException("Category type mismatch: category '" + category.getName() +
                "' is of type " + category.getType() + " but transaction is of type " + request.type());
        }

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setCategory(category);
        transaction.setType(request.type());
        transaction.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        transaction.setDescription(request.description().trim());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setPaymentMethod(request.paymentMethod());
        transaction.setNotes(request.notes() != null ? request.notes().trim() : null);

        // Check budget warning before saving
        String budgetWarning = checkBudgetWarning(userId, category, request.type(), request.amount(), request.transactionDate(), null);

        Transaction saved = transactionRepository.save(transaction);
        log.info("Created transaction id {} for user id {}", saved.getId(), userId);

        return TransactionResponse.fromEntity(saved, budgetWarning);
    }

    /**
     * Retrieves a single transaction by ID scoped to the authenticated user.
     *
     * @param userId ID of the authenticated user
     * @param id ID of the transaction
     * @return TransactionResponse DTO
     */
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Long userId, Long id) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        return TransactionResponse.fromEntity(transaction);
    }

    /**
     * Lists transactions with multi-criteria dynamic filtering, sorting, and pagination.
     *
     * @param userId ID of the authenticated user
     * @param from optional start date filter
     * @param to optional end date filter
     * @param type optional transaction type
     * @param categoryId optional category filter
     * @param paymentMethod optional payment method
     * @param minAmount optional minimum amount
     * @param maxAmount optional maximum amount
     * @param search optional text search on description
     * @param pageable pagination details
     * @return Page of TransactionResponse DTOs
     */
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(
        Long userId,
        LocalDate from,
        LocalDate to,
        TransactionType type,
        Long categoryId,
        PaymentMethod paymentMethod,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String search,
        Pageable pageable
    ) {
        Specification<Transaction> spec = TransactionSpecification.filter(
            userId, from, to, type, categoryId, paymentMethod, minAmount, maxAmount, search
        );
        return transactionRepository.findAll(spec, pageable).map(TransactionResponse::fromEntity);
    }

    /**
     * Updates an existing transaction scoped to the authenticated user.
     *
     * @param userId ID of the authenticated user
     * @param id ID of the transaction to update
     * @param request Update details
     * @return Updated TransactionResponse DTO with optional budget warning
     */
    @Transactional
    public TransactionResponse updateTransaction(Long userId, Long id, TransactionRequest request) {
        validateTransactionRequest(request);

        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));

        Category category = categoryRepository.findAccessibleById(request.categoryId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));

        if (category.getType() != request.type()) {
            throw new BadRequestException("Category type mismatch: category '" + category.getName() +
                "' is of type " + category.getType() + " but transaction is of type " + request.type());
        }

        String budgetWarning = checkBudgetWarning(
            userId, category, request.type(), request.amount(), request.transactionDate(), transaction
        );

        transaction.setCategory(category);
        transaction.setType(request.type());
        transaction.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        transaction.setDescription(request.description().trim());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setPaymentMethod(request.paymentMethod());
        transaction.setNotes(request.notes() != null ? request.notes().trim() : null);

        Transaction updated = transactionRepository.save(transaction);
        log.info("Updated transaction id {} for user id {}", updated.getId(), userId);

        return TransactionResponse.fromEntity(updated, budgetWarning);
    }

    /**
     * Deletes a transaction scoped to the authenticated user.
     *
     * @param userId ID of the authenticated user
     * @param id ID of the transaction to delete
     */
    @Transactional
    public void deleteTransaction(Long userId, Long id) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        transactionRepository.delete(transaction);
        log.info("Deleted transaction id {} for user id {}", id, userId);
    }

    private void validateTransactionRequest(TransactionRequest request) {
        if (request.transactionDate().isAfter(LocalDate.now().plusYears(1))) {
            throw new BadRequestException("Transaction date cannot be more than 1 year in the future");
        }
    }

    /**
     * Checks if saving an expense crosses 80% or 100% of the category or overall budget.
     */
    public String checkBudgetWarning(
        Long userId,
        Category category,
        TransactionType type,
        BigDecimal amount,
        LocalDate transactionDate,
        Transaction existingTx
    ) {
        if (type != TransactionType.EXPENSE) {
            return null;
        }

        YearMonth ym = YearMonth.from(transactionDate);
        String month = ym.toString();
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        StringBuilder warning = new StringBuilder();

        // 1. Check category budget
        Optional<Budget> catBudgetOpt = budgetRepository.findByUserIdAndCategoryIdAndMonth(userId, category.getId(), month);
        if (catBudgetOpt.isPresent()) {
            Budget budget = catBudgetOpt.get();
            BigDecimal existingSpent = transactionRepository.sumCategoryExpensesInDateRange(userId, category.getId(), start, end);
            if (existingTx != null && existingTx.getType() == TransactionType.EXPENSE &&
                existingTx.getCategory().getId().equals(category.getId()) &&
                YearMonth.from(existingTx.getTransactionDate()).equals(ym)) {
                existingSpent = existingSpent.subtract(existingTx.getAmount());
            }
            BigDecimal projectedSpent = existingSpent.add(amount);
            BigDecimal limit = budget.getLimitAmount();

            if (limit.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percent = projectedSpent.multiply(BigDecimal.valueOf(100)).divide(limit, 1, RoundingMode.HALF_UP);
                if (projectedSpent.compareTo(limit) >= 0) {
                    warning.append("Category budget exceeded: ").append(percent).append("% used (limit: ").append(limit).append(")");
                } else if (percent.compareTo(BigDecimal.valueOf(80)) >= 0) {
                    warning.append("Category budget warning: ").append(percent).append("% used (limit: ").append(limit).append(")");
                }
            }
        }

        // 2. Check overall monthly budget
        Optional<Budget> overallBudgetOpt = budgetRepository.findByUserIdAndCategoryIsNullAndMonth(userId, month);
        if (overallBudgetOpt.isPresent()) {
            Budget overallBudget = overallBudgetOpt.get();
            BigDecimal existingSpent = transactionRepository.sumOverallExpensesInDateRange(userId, start, end);
            if (existingTx != null && existingTx.getType() == TransactionType.EXPENSE &&
                YearMonth.from(existingTx.getTransactionDate()).equals(ym)) {
                existingSpent = existingSpent.subtract(existingTx.getAmount());
            }
            BigDecimal projectedSpent = existingSpent.add(amount);
            BigDecimal limit = overallBudget.getLimitAmount();

            if (limit.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percent = projectedSpent.multiply(BigDecimal.valueOf(100)).divide(limit, 1, RoundingMode.HALF_UP);
                if (projectedSpent.compareTo(limit) >= 0) {
                    if (warning.length() > 0) warning.append(" | ");
                    warning.append("Overall monthly budget exceeded: ").append(percent).append("% used (limit: ").append(limit).append(")");
                } else if (percent.compareTo(BigDecimal.valueOf(80)) >= 0) {
                    if (warning.length() > 0) warning.append(" | ");
                    warning.append("Overall monthly budget warning: ").append(percent).append("% used (limit: ").append(limit).append(")");
                }
            }
        }

        return warning.length() > 0 ? warning.toString() : null;
    }
}
