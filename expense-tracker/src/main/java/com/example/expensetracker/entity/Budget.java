package com.example.expensetracker.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "budgets", uniqueConstraints = {
    @UniqueConstraint(name = "uq_budgets_user_category_month", columnNames = {"user_id", "category_id", "month"})
}, indexes = {
    @Index(name = "idx_budgets_user_month", columnList = "user_id, month")
})
public class Budget extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "`month`", nullable = false, length = 7)
    private String month; // Format: YYYY-MM

    @Column(name = "limit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal limitAmount;

    public Budget() {
    }

    public Budget(Long id, User user, Category category, String month, BigDecimal limitAmount) {
        this.id = id;
        this.user = user;
        this.category = category;
        this.month = month;
        this.limitAmount = limitAmount;
    }

    public boolean isOverallBudget() {
        return this.category == null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(BigDecimal limitAmount) {
        this.limitAmount = limitAmount;
    }
}
