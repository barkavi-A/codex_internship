package com.example.expensetracker.repository;

import com.example.expensetracker.entity.Transaction;
import com.example.expensetracker.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT t FROM Transaction t WHERE t.id = :id AND t.user.id = :userId")
    Optional<Transaction> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Override
    @EntityGraph(attributePaths = {"category"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.type = :type AND t.transactionDate BETWEEN :from AND :to")
    BigDecimal sumByUserAndTypeAndDateBetween(
        @Param("userId") Long userId,
        @Param("type") TransactionType type,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    @Query("SELECT COUNT(t) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.transactionDate BETWEEN :from AND :to")
    long countByUserAndDateBetween(
        @Param("userId") Long userId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    @Query("SELECT COALESCE(MAX(t.amount), 0) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.transactionDate BETWEEN :from AND :to")
    BigDecimal highestExpenseByUserAndDateBetween(
        @Param("userId") Long userId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    @Query("SELECT c.id, c.name, c.color, c.icon, SUM(t.amount) " +
           "FROM Transaction t JOIN t.category c " +
           "WHERE t.user.id = :userId AND t.type = :type AND t.transactionDate BETWEEN :from AND :to " +
           "GROUP BY c.id, c.name, c.color, c.icon ORDER BY SUM(t.amount) DESC")
    List<Object[]> aggregateByCategory(
        @Param("userId") Long userId,
        @Param("type") TransactionType type,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    @Query("SELECT t.transactionDate, SUM(t.amount) " +
           "FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.transactionDate BETWEEN :from AND :to " +
           "GROUP BY t.transactionDate ORDER BY t.transactionDate ASC")
    List<Object[]> aggregateDailyExpenses(
        @Param("userId") Long userId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    @Query("SELECT EXTRACT(MONTH FROM t.transactionDate) AS monthNum, t.type AS type, SUM(t.amount) AS total " +
           "FROM Transaction t " +
           "WHERE t.user.id = :userId AND EXTRACT(YEAR FROM t.transactionDate) = :year " +
           "GROUP BY EXTRACT(MONTH FROM t.transactionDate), t.type")
    List<Object[]> aggregateMonthlyForYear(
        @Param("userId") Long userId,
        @Param("year") int year
    );

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.category.id = :categoryId " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumCategoryExpensesInDateRange(
        @Param("userId") Long userId,
        @Param("categoryId") Long categoryId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.type = 'EXPENSE' " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumOverallExpensesInDateRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.type = 'EXPENSE' " +
           "AND t.transactionDate BETWEEN :from AND :to ORDER BY t.amount DESC")
    List<Transaction> findTopExpenses(
        @Param("userId") Long userId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
           "AND t.transactionDate BETWEEN :from AND :to ORDER BY t.transactionDate ASC, t.id ASC")
    List<Transaction> findForExport(
        @Param("userId") Long userId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );
}
