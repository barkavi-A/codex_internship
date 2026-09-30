package com.example.expensetracker.controller;

import com.example.expensetracker.dto.request.BudgetRequest;
import com.example.expensetracker.dto.response.BudgetResponse;
import com.example.expensetracker.dto.response.BudgetStatusResponse;
import com.example.expensetracker.security.UserPrincipal;
import com.example.expensetracker.service.BudgetService;
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
@RequestMapping("/api/budgets")
@Tag(name = "Budgets", description = "Endpoints for managing monthly spending budgets and alerts")
public class BudgetController {

    private static final Logger log = LoggerFactory.getLogger(BudgetController.class);

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    @Operation(summary = "Set a budget for a category or overall month")
    public ResponseEntity<BudgetResponse> createBudget(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @Valid @RequestBody BudgetRequest request
    ) {
        log.info("Creating budget for user id: {}", userPrincipal.getId());
        BudgetResponse response = budgetService.createBudget(userPrincipal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List budgets for the user")
    public ResponseEntity<List<BudgetResponse>> getBudgets(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @RequestParam(required = false) String month
    ) {
        log.info("Fetching budgets for user id: {}, month: {}", userPrincipal.getId(), month);
        List<BudgetResponse> responses = budgetService.getBudgets(userPrincipal.getId(), month);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single budget by ID")
    public ResponseEntity<BudgetResponse> getBudget(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @PathVariable Long id
    ) {
        log.info("Fetching budget id {} for user id: {}", id, userPrincipal.getId());
        BudgetResponse response = budgetService.getBudget(userPrincipal.getId(), id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing budget limit")
    public ResponseEntity<BudgetResponse> updateBudget(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @PathVariable Long id,
        @Valid @RequestBody BudgetRequest request
    ) {
        log.info("Updating budget id {} for user id: {}", id, userPrincipal.getId());
        BudgetResponse response = budgetService.updateBudget(userPrincipal.getId(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an existing budget")
    public ResponseEntity<Void> deleteBudget(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @PathVariable Long id
    ) {
        log.info("Deleting budget id {} for user id: {}", id, userPrincipal.getId());
        budgetService.deleteBudget(userPrincipal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    @Operation(summary = "Get budget status and progress for a month")
    public ResponseEntity<List<BudgetStatusResponse>> getBudgetStatus(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @RequestParam(required = false) String month
    ) {
        log.info("Fetching budget status for user id: {}, month: {}", userPrincipal.getId(), month);
        List<BudgetStatusResponse> responses = budgetService.getBudgetStatus(userPrincipal.getId(), month);
        return ResponseEntity.ok(responses);
    }
}
