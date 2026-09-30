package com.example.expensetracker.service;

import com.example.expensetracker.dto.request.CategoryRequest;
import com.example.expensetracker.dto.response.CategoryResponse;
import com.example.expensetracker.entity.Category;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.exception.ConflictException;
import com.example.expensetracker.exception.ResourceNotFoundException;
import com.example.expensetracker.repository.CategoryRepository;
import com.example.expensetracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service managing categories for income and expenses.
 */
@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public CategoryService(CategoryRepository categoryRepository, UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    /**
     * Retrieves all categories accessible to the user (global system categories + custom user categories).
     *
     * @param userId ID of the authenticated user
     * @return List of CategoryResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(Long userId) {
        List<Category> categories = categoryRepository.findGlobalAndUserCategories(userId);
        return categories.stream().map(CategoryResponse::fromEntity).toList();
    }

    /**
     * Creates a new custom category for the user.
     *
     * @param userId ID of the authenticated user
     * @param request Category creation details
     * @return CategoryResponse DTO
     */
    @Transactional
    public CategoryResponse createCategory(Long userId, CategoryRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String trimmedName = request.name().trim();
        // Check for duplicates in user custom categories or global categories
        if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndType(userId, trimmedName, request.type()) ||
            categoryRepository.existsByUserIdIsNullAndNameIgnoreCaseAndType(trimmedName, request.type())) {
            throw new ConflictException("A category with name '" + trimmedName + "' and type '" + request.type() + "' already exists");
        }

        Category category = new Category();
        category.setUser(user);
        category.setName(trimmedName);
        category.setType(request.type());
        category.setColor(request.color() != null && !request.color().isBlank() ? request.color().trim() : "#64748B");
        category.setIcon(request.icon() != null && !request.icon().isBlank() ? request.icon().trim() : "tag");

        Category saved = categoryRepository.save(category);
        log.info("Created custom category id {} for user id {}", saved.getId(), userId);
        return CategoryResponse.fromEntity(saved);
    }

    /**
     * Updates an existing user category. Global categories cannot be updated.
     *
     * @param userId ID of the authenticated user
     * @param categoryId ID of the category
     * @param request Category update details
     * @return Updated CategoryResponse DTO
     */
    @Transactional
    public CategoryResponse updateCategory(Long userId, Long categoryId, CategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + categoryId));

        if (category.isGlobal()) {
            throw new ConflictException("Global default categories cannot be modified");
        }

        if (!category.getUser().getId().equals(userId)) {
            // Return 404 rather than 403 to prevent ID leaking
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }

        String trimmedName = request.name().trim();
        if (!category.getName().equalsIgnoreCase(trimmedName) || !category.getType().equals(request.type())) {
            if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndType(userId, trimmedName, request.type()) ||
                categoryRepository.existsByUserIdIsNullAndNameIgnoreCaseAndType(trimmedName, request.type())) {
                throw new ConflictException("A category with name '" + trimmedName + "' and type '" + request.type() + "' already exists");
            }
        }

        category.setName(trimmedName);
        category.setType(request.type());
        if (request.color() != null && !request.color().isBlank()) {
            category.setColor(request.color().trim());
        }
        if (request.icon() != null && !request.icon().isBlank()) {
            category.setIcon(request.icon().trim());
        }

        Category saved = categoryRepository.save(category);
        log.info("Updated category id {} for user id {}", saved.getId(), userId);
        return CategoryResponse.fromEntity(saved);
    }

    /**
     * Deletes a user category. Cannot delete if it has existing transactions or if it's a global category.
     *
     * @param userId ID of the authenticated user
     * @param categoryId ID of the category to delete
     */
    @Transactional
    public void deleteCategory(Long userId, Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + categoryId));

        if (category.isGlobal()) {
            throw new ConflictException("Global default categories cannot be deleted");
        }

        if (!category.getUser().getId().equals(userId)) {
            // Return 404 rather than 403 to prevent ID leaking
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }

        if (categoryRepository.hasTransactions(categoryId)) {
            throw new ConflictException("Cannot delete category because it has associated transactions");
        }

        categoryRepository.delete(category);
        log.info("Deleted category id {} for user id {}", categoryId, userId);
    }
}
