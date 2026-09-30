package com.example.expensetracker.repository;

import com.example.expensetracker.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    @Query("SELECT b FROM Budget b LEFT JOIN FETCH b.category WHERE b.user.id = :userId AND b.month = :month ORDER BY b.category.name ASC NULLS FIRST")
    List<Budget> findByUserIdAndMonth(@Param("userId") Long userId, @Param("month") String month);

    @Query("SELECT b FROM Budget b LEFT JOIN FETCH b.category WHERE b.user.id = :userId ORDER BY b.month DESC")
    List<Budget> findByUserId(@Param("userId") Long userId);

    @Query("SELECT b FROM Budget b LEFT JOIN FETCH b.category WHERE b.id = :id AND b.user.id = :userId")
    Optional<Budget> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    Optional<Budget> findByUserIdAndCategoryIdAndMonth(Long userId, Long categoryId, String month);

    Optional<Budget> findByUserIdAndCategoryIsNullAndMonth(Long userId, String month);

    boolean existsByUserIdAndCategoryIdAndMonth(Long userId, Long categoryId, String month);

    boolean existsByUserIdAndCategoryIsNullAndMonth(Long userId, String month);
}
