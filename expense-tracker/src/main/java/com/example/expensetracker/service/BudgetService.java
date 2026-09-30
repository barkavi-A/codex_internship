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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing monthly budget limits and status calculations.
 */
@Service
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public BudgetService(
        BudgetRepository budgetRepository,
        CategoryRepository categoryRepository,
        UserRepository userRepository,
        TransactionRepository transactionRepository
    ) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Creates a new category budget or overall monthly budget.
     *
     * @param userId ID of the authenticated user
     * @param request Budget details
     * @return BudgetResponse DTO
     */
    @Transactional
    public BudgetResponse createBudget(Long userId, BudgetRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findAccessibleById(request.categoryId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));
            if (category.getType() != TransactionType.EXPENSE) {
                throw new BadRequestException("Budgets can only be set for EXPENSE categories");
            }

            if (budgetRepository.existsByUserIdAndCategoryIdAndMonth(userId, request.categoryId(), request.month())) {
                throw new ConflictException("A budget for this category and month already exists");
            }
        } else {
            if (budgetRepository.existsByUserIdAndCategoryIsNullAndMonth(userId, request.month())) {
                throw new ConflictException("An overall monthly budget for " + request.month() + " already exists");
            }
        }

        Budget budget = new Budget();
        budget.setUser(user);
        budget.setCategory(category);
        budget.setMonth(request.month());
        budget.setLimitAmount(request.limitAmount().setScale(2, RoundingMode.HALF_UP));

        Budget saved = budgetRepository.save(budget);
        log.info("Created budget id {} for user id {}", saved.getId(), userId);
        return BudgetResponse.fromEntity(saved);
    }

    /**
     * Lists budgets for the user, optionally filtered by month.
     *
     * @param userId ID of the authenticated user
     * @param month optional month in YYYY-MM format
     * @return List of BudgetResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<BudgetResponse> getBudgets(Long userId, String month) {
        List<Budget> budgets;
        if (month != null && !month.isBlank()) {
            budgets = budgetRepository.findByUserIdAndMonth(userId, month.trim());
        } else {
            budgets = budgetRepository.findByUserId(userId);
        }
        return budgets.stream().map(BudgetResponse::fromEntity).toList();
    }

    /**
     * Retrieves a single budget by ID.
     *
     * @param userId ID of the authenticated user
     * @param id ID of the budget
     * @return BudgetResponse DTO
     */
    @Transactional(readOnly = true)
    public BudgetResponse getBudget(Long userId, Long id) {
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Budget not found with id: " + id));
        return BudgetResponse.fromEntity(budget);
    }

    /**
     * Updates an existing budget.
     *
     * @param userId ID of the authenticated user
     * @param id ID of the budget
     * @param request Update details
     * @return Updated BudgetResponse DTO
     */
    @Transactional
    public BudgetResponse updateBudget(Long userId, Long id, BudgetRequest request) {
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Budget not found with id: " + id));

        budget.setLimitAmount(request.limitAmount().setScale(2, RoundingMode.HALF_UP));
        Budget updated = budgetRepository.save(budget);
        log.info("Updated budget id {} for user id {}", updated.getId(), userId);
        return BudgetResponse.fromEntity(updated);
    }

    /**
     * Deletes a budget.
     *
     * @param userId ID of the authenticated user
     * @param id ID of the budget to delete
     */
    @Transactional
    public void deleteBudget(Long userId, Long id) {
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Budget not found with id: " + id));
        budgetRepository.delete(budget);
        log.info("Deleted budget id {} for user id {}", id, userId);
    }

    /**
     * Computes the budget utilization and status for a given month.
     *
     * @param userId ID of the authenticated user
     * @param month Month in YYYY-MM format
     * @return List of BudgetStatusResponse
     */
    @Transactional(readOnly = true)
    public List<BudgetStatusResponse> getBudgetStatus(Long userId, String month) {
        String targetMonth = (month != null && !month.isBlank())
            ? month.trim()
            : YearMonth.now().toString();

        YearMonth ym = YearMonth.parse(targetMonth);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Budget> budgets = budgetRepository.findByUserIdAndMonth(userId, targetMonth);
        List<BudgetStatusResponse> statuses = new ArrayList<>();

        for (Budget budget : budgets) {
            BigDecimal spent;
            Long categoryId = null;
            String categoryName;

            if (budget.isOverallBudget()) {
                spent = transactionRepository.sumOverallExpensesInDateRange(userId, start, end);
                categoryName = "Overall Monthly Budget";
            } else {
                categoryId = budget.getCategory().getId();
                categoryName = budget.getCategory().getName();
                spent = transactionRepository.sumCategoryExpensesInDateRange(userId, categoryId, start, end);
            }

            BigDecimal limit = budget.getLimitAmount();
            BigDecimal remaining = limit.subtract(spent);

            BigDecimal percentUsed = BigDecimal.ZERO;
            if (limit.compareTo(BigDecimal.ZERO) > 0) {
                percentUsed = spent.multiply(BigDecimal.valueOf(100)).divide(limit, 2, RoundingMode.HALF_UP);
            }

            BudgetStatus status;
            if (percentUsed.compareTo(BigDecimal.valueOf(100)) >= 0) {
                status = BudgetStatus.EXCEEDED;
            } else if (percentUsed.compareTo(BigDecimal.valueOf(80)) >= 0) {
                status = BudgetStatus.WARNING;
            } else {
                status = BudgetStatus.OK;
            }

            statuses.add(new BudgetStatusResponse(
                budget.getId(),
                categoryId,
                categoryName,
                targetMonth,
                limit,
                spent,
                remaining,
                percentUsed,
                status
            ));
        }

        return statuses;
    }
}
