package com.example.expensetracker.repository;

import com.example.expensetracker.entity.Category;
import com.example.expensetracker.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("SELECT c FROM Category c WHERE c.user.id IS NULL OR c.user.id = :userId ORDER BY c.type ASC, c.name ASC")
    List<Category> findGlobalAndUserCategories(@Param("userId") Long userId);

    List<Category> findByUserIdOrderByNameAsc(Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT c FROM Category c WHERE c.id = :id AND (c.user.id IS NULL OR c.user.id = :userId)")
    Optional<Category> findAccessibleById(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByUserIdAndNameIgnoreCaseAndType(Long userId, String name, TransactionType type);

    boolean existsByUserIdIsNullAndNameIgnoreCaseAndType(String name, TransactionType type);

    @Query("SELECT COUNT(t) > 0 FROM Transaction t WHERE t.category.id = :categoryId")
    boolean hasTransactions(@Param("categoryId") Long categoryId);
}
