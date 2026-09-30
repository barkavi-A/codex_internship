package com.example.expensetracker.repository;

import com.example.expensetracker.entity.Budget;
import com.example.expensetracker.entity.Category;
import com.example.expensetracker.entity.Transaction;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.enums.PaymentMethod;
import com.example.expensetracker.enums.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Test
    @DisplayName("Should enforce unique constraint on user email")
    void testUserEmailUniqueConstraint() {
        User u1 = new User(null, "User 1", "duplicate@test.com", "pass", "INR");
        entityManager.persistAndFlush(u1);

        User u2 = new User(null, "User 2", "duplicate@test.com", "pass2", "INR");
        assertThrows(Exception.class, () -> {
            entityManager.persistAndFlush(u2);
        });
    }

    @Test
    @DisplayName("Should enforce unique constraint on category (user_id, name, type)")
    void testCategoryUniqueConstraint() {
        User user = new User(null, "User Cat", "cat@test.com", "pass", "INR");
        entityManager.persistAndFlush(user);

        Category c1 = new Category(null, user, "Dining", TransactionType.EXPENSE, "#FF0000", "food");
        entityManager.persistAndFlush(c1);

        Category c2 = new Category(null, user, "Dining", TransactionType.EXPENSE, "#00FF00", "food2");
        assertThrows(Exception.class, () -> {
            entityManager.persistAndFlush(c2);
        });
    }

    @Test
    @DisplayName("Should enforce unique constraint on budget (user_id, category_id, month)")
    void testBudgetUniqueConstraint() {
        User user = new User(null, "User Bud", "bud@test.com", "pass", "INR");
        entityManager.persistAndFlush(user);

        Category c1 = new Category(null, user, "Shopping", TransactionType.EXPENSE, "#FF0000", "bag");
        entityManager.persistAndFlush(c1);

        Budget b1 = new Budget(null, user, c1, "2026-09", BigDecimal.valueOf(1000.00));
        entityManager.persistAndFlush(b1);

        Budget b2 = new Budget(null, user, c1, "2026-09", BigDecimal.valueOf(2000.00));
        assertThrows(Exception.class, () -> {
            entityManager.persistAndFlush(b2);
        });
    }

    @Test
    @DisplayName("Should filter transactions using dynamic specifications")
    void testTransactionSpecificationFiltering() {
        User user = new User(null, "Spec User", "spec@test.com", "pass", "INR");
        entityManager.persistAndFlush(user);

        Category cat1 = new Category(null, user, "Groceries", TransactionType.EXPENSE, "#FF0000", "food");
        Category cat2 = new Category(null, user, "Tech", TransactionType.EXPENSE, "#0000FF", "laptop");
        entityManager.persistAndFlush(cat1);
        entityManager.persistAndFlush(cat2);

        Transaction t1 = new Transaction(null, user, cat1, TransactionType.EXPENSE,
            BigDecimal.valueOf(150.00), "Supermarket organic milk", LocalDate.of(2026, 9, 10),
            PaymentMethod.CARD, null);
        Transaction t2 = new Transaction(null, user, cat2, TransactionType.EXPENSE,
            BigDecimal.valueOf(500.00), "Wireless Mouse", LocalDate.of(2026, 9, 15),
            PaymentMethod.UPI, null);
        entityManager.persistAndFlush(t1);
        entityManager.persistAndFlush(t2);

        // Filter by keyword "milk"
        Specification<Transaction> spec1 = TransactionSpecification.filter(
            user.getId(), null, null, null, null, null, null, null, "milk"
        );
        Page<Transaction> page1 = transactionRepository.findAll(spec1, PageRequest.of(0, 10));
        assertEquals(1, page1.getTotalElements());
        assertEquals("Supermarket organic milk", page1.getContent().get(0).getDescription());

        // Filter by minAmount 200
        Specification<Transaction> spec2 = TransactionSpecification.filter(
            user.getId(), null, null, null, null, null, BigDecimal.valueOf(200.00), null, null
        );
        Page<Transaction> page2 = transactionRepository.findAll(spec2, PageRequest.of(0, 10));
        assertEquals(1, page2.getTotalElements());
        assertEquals("Wireless Mouse", page2.getContent().get(0).getDescription());
    }

    @Test
    @DisplayName("Should compute database-side category aggregations correctly")
    void testAggregateByCategory() {
        User user = new User(null, "Agg User", "agg@test.com", "pass", "INR");
        entityManager.persistAndFlush(user);

        Category cat = new Category(null, user, "Books", TransactionType.EXPENSE, "#123456", "book");
        entityManager.persistAndFlush(cat);

        Transaction t1 = new Transaction(null, user, cat, TransactionType.EXPENSE,
            BigDecimal.valueOf(200.00), "Novel", LocalDate.of(2026, 9, 10),
            PaymentMethod.CASH, null);
        Transaction t2 = new Transaction(null, user, cat, TransactionType.EXPENSE,
            BigDecimal.valueOf(300.00), "Textbook", LocalDate.of(2026, 9, 12),
            PaymentMethod.CASH, null);
        entityManager.persistAndFlush(t1);
        entityManager.persistAndFlush(t2);

        List<Object[]> agg = transactionRepository.aggregateByCategory(
            user.getId(), TransactionType.EXPENSE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        );

        assertEquals(1, agg.size());
        assertEquals("Books", agg.get(0)[1]);
        assertEquals(BigDecimal.valueOf(500.00).setScale(2), ((BigDecimal) agg.get(0)[4]).setScale(2));
    }

    @Test
    @DisplayName("Should compute category and overall expenses in date range")
    void testSumExpensesInDateRange() {
        User user = new User(null, "Sum User", "sum@test.com", "pass", "INR");
        entityManager.persistAndFlush(user);

        Category cat = new Category(null, user, "Fuel", TransactionType.EXPENSE, "#123", "gas");
        entityManager.persistAndFlush(cat);

        Transaction t = new Transaction(null, user, cat, TransactionType.EXPENSE,
            BigDecimal.valueOf(750.00), "Petrol", LocalDate.of(2026, 9, 5),
            PaymentMethod.UPI, null);
        entityManager.persistAndFlush(t);

        BigDecimal catSum = transactionRepository.sumCategoryExpensesInDateRange(
            user.getId(), cat.getId(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        );
        assertEquals(BigDecimal.valueOf(750.00).setScale(2), catSum.setScale(2));

        BigDecimal overallSum = transactionRepository.sumOverallExpensesInDateRange(
            user.getId(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        );
        assertEquals(BigDecimal.valueOf(750.00).setScale(2), overallSum.setScale(2));
    }
}
