package com.example.expensetracker.controller;

import com.example.expensetracker.dto.request.CategoryRequest;
import com.example.expensetracker.dto.response.CategoryResponse;
import com.example.expensetracker.security.UserPrincipal;
import com.example.expensetracker.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories", description = "Endpoints for managing categories")
public class CategoryController {

    private static final Logger log = LoggerFactory.getLogger(CategoryController.class);

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "List all categories (global defaults + user customized)")
    public ResponseEntity<List<CategoryResponse>> getCategories(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Fetching categories for user id: {}", userPrincipal.getId());
        List<CategoryResponse> categories = categoryService.getCategories(userPrincipal.getId());
        return ResponseEntity.ok(categories);
    }

    @PostMapping
    @Operation(summary = "Create a custom category for the user")
    public ResponseEntity<CategoryResponse> createCategory(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @Valid @RequestBody CategoryRequest request
    ) {
        log.info("Creating custom category for user id: {}", userPrincipal.getId());
        CategoryResponse response = categoryService.createCategory(userPrincipal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a custom user category")
    public ResponseEntity<CategoryResponse> updateCategory(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @PathVariable Long id,
        @Valid @RequestBody CategoryRequest request
    ) {
        log.info("Updating category id {} for user id: {}", id, userPrincipal.getId());
        CategoryResponse response = categoryService.updateCategory(userPrincipal.getId(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a custom user category")
    public ResponseEntity<Void> deleteCategory(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @PathVariable Long id
    ) {
        log.info("Deleting category id {} for user id: {}", id, userPrincipal.getId());
        categoryService.deleteCategory(userPrincipal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
