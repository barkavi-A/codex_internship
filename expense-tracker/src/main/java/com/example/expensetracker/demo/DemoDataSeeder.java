package com.example.expensetracker.demo;

import com.example.expensetracker.entity.Budget;
import com.example.expensetracker.entity.Category;
import com.example.expensetracker.entity.Transaction;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.enums.PaymentMethod;
import com.example.expensetracker.enums.TransactionType;
import com.example.expensetracker.repository.BudgetRepository;
import com.example.expensetracker.repository.CategoryRepository;
import com.example.expensetracker.repository.TransactionRepository;
import com.example.expensetracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Seeds demo data ONLY when the application is launched with the "demo" profile.
 * Creates a demo user and ~100 realistic transactions across the past 6 months.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
        UserRepository userRepository,
        CategoryRepository categoryRepository,
        TransactionRepository transactionRepository,
        BudgetRepository budgetRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String demoEmail = "demo@expensetracker.com";
        if (userRepository.existsByEmail(demoEmail)) {
            log.info("Demo user already exists. Skipping demo data seeding.");
            return;
        }

        log.info("Starting demo data seeding under 'demo' profile...");

        // 1. Create Demo User
        User demoUser = new User();
        demoUser.setName("Alex Mercer");
        demoUser.setEmail(demoEmail);
        demoUser.setPasswordHash(passwordEncoder.encode("Password123"));
        demoUser.setCurrency("INR");
        demoUser = userRepository.save(demoUser);

        // 2. Fetch categories
        List<Category> allCategories = categoryRepository.findGlobalAndUserCategories(demoUser.getId());
        Map<String, Category> catMap = new HashMap<>();
        for (Category c : allCategories) {
            catMap.put(c.getName().toLowerCase(), c);
        }

        Category salaryCat = catMap.get("salary");
        Category businessCat = catMap.get("business");
        Category foodCat = catMap.get("food");
        Category groceriesCat = catMap.get("groceries");
        Category rentCat = catMap.get("rent");
        Category utilitiesCat = catMap.get("utilities");
        Category transportCat = catMap.get("transport");
        Category entertainmentCat = catMap.get("entertainment");
        Category shoppingCat = catMap.get("shopping");
        Category healthCat = catMap.get("health");

        LocalDate today = LocalDate.now();
        Random random = new Random(42); // deterministic seed for reproducibility

        List<Transaction> transactions = new ArrayList<>();

        // Generate ~100 transactions over the last 6 months (180 days)
        for (int m = 5; m >= 0; m--) {
            LocalDate monthDate = today.minusMonths(m);
            YearMonth ym = YearMonth.from(monthDate);
            int daysInMonth = ym.lengthOfMonth();

            // Monthly Salary (Income)
            if (salaryCat != null) {
                Transaction salary = new Transaction(
                    null, demoUser, salaryCat, TransactionType.INCOME,
                    BigDecimal.valueOf(85000.00).setScale(2, RoundingMode.HALF_UP),
                    "Monthly Salary (" + ym + ")",
                    ym.atDay(1),
                    PaymentMethod.BANK_TRANSFER,
                    "Direct deposit"
                );
                transactions.add(salary);
            }

            // Occasional Freelance / Business income
            if (businessCat != null && m % 2 == 0) {
                Transaction freelance = new Transaction(
                    null, demoUser, businessCat, TransactionType.INCOME,
                    BigDecimal.valueOf(15000.00).setScale(2, RoundingMode.HALF_UP),
                    "Freelance Consulting Project",
                    ym.atDay(15),
                    PaymentMethod.BANK_TRANSFER,
                    "Client payment"
                );
                transactions.add(freelance);
            }

            // Rent
            if (rentCat != null) {
                Transaction rent = new Transaction(
                    null, demoUser, rentCat, TransactionType.EXPENSE,
                    BigDecimal.valueOf(25000.00).setScale(2, RoundingMode.HALF_UP),
                    "Apartment Rent (" + ym + ")",
                    ym.atDay(3),
                    PaymentMethod.BANK_TRANSFER,
                    "Paid via NEFT"
                );
                transactions.add(rent);
            }

            // Utilities
            if (utilitiesCat != null) {
                Transaction util = new Transaction(
                    null, demoUser, utilitiesCat, TransactionType.EXPENSE,
                    BigDecimal.valueOf(3200.00 + random.nextInt(800)).setScale(2, RoundingMode.HALF_UP),
                    "Electricity & Broadband Bill",
                    ym.atDay(7),
                    PaymentMethod.UPI,
                    "Online payment"
                );
                transactions.add(util);
            }

            // Multiple Food & Dining entries
            if (foodCat != null) {
                String[] foodDesc = {"Cafe Coffee & Snacks", "Dinner with Colleagues", "Weekend Brunch", "Food Delivery", "Quick Lunch"};
                for (int i = 0; i < 4; i++) {
                    int day = Math.min(daysInMonth, 2 + i * 6 + random.nextInt(3));
                    transactions.add(new Transaction(
                        null, demoUser, foodCat, TransactionType.EXPENSE,
                        BigDecimal.valueOf(350 + random.nextInt(1200)).setScale(2, RoundingMode.HALF_UP),
                        foodDesc[i % foodDesc.length],
                        ym.atDay(day),
                        PaymentMethod.UPI,
                        "Restaurant visit"
                    ));
                }
            }

            // Groceries
            if (groceriesCat != null) {
                for (int i = 0; i < 3; i++) {
                    int day = Math.min(daysInMonth, 5 + i * 9);
                    transactions.add(new Transaction(
                        null, demoUser, groceriesCat, TransactionType.EXPENSE,
                        BigDecimal.valueOf(1200 + random.nextInt(1800)).setScale(2, RoundingMode.HALF_UP),
                        "Supermarket Groceries & Provisions",
                        ym.atDay(day),
                        PaymentMethod.CARD,
                        "Weekly supplies"
                    ));
                }
            }

            // Transport & Fuel
            if (transportCat != null) {
                for (int i = 0; i < 3; i++) {
                    int day = Math.min(daysInMonth, 4 + i * 8);
                    transactions.add(new Transaction(
                        null, demoUser, transportCat, TransactionType.EXPENSE,
                        BigDecimal.valueOf(250 + random.nextInt(600)).setScale(2, RoundingMode.HALF_UP),
                        "Metro & Cab Rides",
                        ym.atDay(day),
                        PaymentMethod.UPI,
                        "Daily commute"
                    ));
                }
            }

            // Shopping
            if (shoppingCat != null && m != 3) {
                int day = Math.min(daysInMonth, 12 + random.nextInt(10));
                transactions.add(new Transaction(
                    null, demoUser, shoppingCat, TransactionType.EXPENSE,
                    BigDecimal.valueOf(1500 + random.nextInt(3500)).setScale(2, RoundingMode.HALF_UP),
                    "Apparel & Essentials Shopping",
                    ym.atDay(day),
                    PaymentMethod.CARD,
                    "Retail store"
                ));
            }

            // Entertainment
            if (entertainmentCat != null) {
                int day = Math.min(daysInMonth, 18 + random.nextInt(7));
                transactions.add(new Transaction(
                    null, demoUser, entertainmentCat, TransactionType.EXPENSE,
                    BigDecimal.valueOf(600 + random.nextInt(1400)).setScale(2, RoundingMode.HALF_UP),
                    "Cinema Movie Tickets & OTT",
                    ym.atDay(day),
                    PaymentMethod.CARD,
                    "Weekend outing"
                ));
            }

            // Health
            if (healthCat != null && m % 2 == 1) {
                int day = Math.min(daysInMonth, 22);
                transactions.add(new Transaction(
                    null, demoUser, healthCat, TransactionType.EXPENSE,
                    BigDecimal.valueOf(800 + random.nextInt(1500)).setScale(2, RoundingMode.HALF_UP),
                    "Pharmacy Medicines & Vitamins",
                    ym.atDay(day),
                    PaymentMethod.UPI,
                    "Health expenses"
                ));
            }
        }

        transactionRepository.saveAll(transactions);
        log.info("Seeded {} demo transactions for user alex@expensetracker.com", transactions.size());

        // 3. Create Demo Budgets for Current and Previous Month
        YearMonth currentYm = YearMonth.from(today);
        String currentMonthStr = currentYm.toString();

        // Overall Monthly Budget
        Budget overallBudget = new Budget(
            null, demoUser, null, currentMonthStr,
            BigDecimal.valueOf(60000.00).setScale(2, RoundingMode.HALF_UP)
        );
        budgetRepository.save(overallBudget);

        // Category Budgets
        if (foodCat != null) {
            // Set Food budget around 5000 so it can trigger warning or ok
            budgetRepository.save(new Budget(
                null, demoUser, foodCat, currentMonthStr,
                BigDecimal.valueOf(5000.00).setScale(2, RoundingMode.HALF_UP)
            ));
        }

        if (entertainmentCat != null) {
            // Set tight entertainment budget to demonstrate EXCEEDED or WARNING
            budgetRepository.save(new Budget(
                null, demoUser, entertainmentCat, currentMonthStr,
                BigDecimal.valueOf(1000.00).setScale(2, RoundingMode.HALF_UP)
            ));
        }

        if (rentCat != null) {
            budgetRepository.save(new Budget(
                null, demoUser, rentCat, currentMonthStr,
                BigDecimal.valueOf(25000.00).setScale(2, RoundingMode.HALF_UP)
            ));
        }

        log.info("Demo data seeding completed successfully! Total transactions: {}", transactions.size());
    }
}
