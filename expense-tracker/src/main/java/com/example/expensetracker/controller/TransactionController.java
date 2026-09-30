package com.example.expensetracker.controller;

import com.example.expensetracker.dto.request.TransactionRequest;
import com.example.expensetracker.dto.response.TransactionResponse;
import com.example.expensetracker.enums.PaymentMethod;
import com.example.expensetracker.enums.TransactionType;
import com.example.expensetracker.security.UserPrincipal;
import com.example.expensetracker.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Endpoints for managing financial transactions")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @Operation(summary = "Create a new transaction with budget checking")
    public ResponseEntity<TransactionResponse> createTransaction(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @Valid @RequestBody TransactionRequest request
    ) {
        log.info("Creating transaction for user id: {}", userPrincipal.getId());
        TransactionResponse response = transactionService.createTransaction(userPrincipal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single transaction by ID")
    public ResponseEntity<TransactionResponse> getTransaction(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @PathVariable Long id
    ) {
        log.info("Fetching transaction id {} for user id: {}", id, userPrincipal.getId());
        TransactionResponse response = transactionService.getTransaction(userPrincipal.getId(), id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get transactions with pagination, sorting, and dynamic filters")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) TransactionType type,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) PaymentMethod paymentMethod,
        @RequestParam(required = false) BigDecimal minAmount,
        @RequestParam(required = false) BigDecimal maxAmount,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "transactionDate,desc") String sort
    ) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.min(100, Math.max(1, size));

        String[] sortParts = sort.split(",");
        String sortProp = sortParts[0];
        Sort.Direction direction = (sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1]))
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;

        // Map allowed sort properties safely
        if (!sortProp.equals("amount") && !sortProp.equals("transactionDate") && !sortProp.equals("id")) {
            sortProp = "transactionDate";
        }

        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(direction, sortProp));

        Page<TransactionResponse> results = transactionService.getTransactions(
            userPrincipal.getId(), from, to, type, categoryId, paymentMethod, minAmount, maxAmount, search, pageable
        );

        return ResponseEntity.ok(results);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing transaction")
    public ResponseEntity<TransactionResponse> updateTransaction(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @PathVariable Long id,
        @Valid @RequestBody TransactionRequest request
    ) {
        log.info("Updating transaction id {} for user id: {}", id, userPrincipal.getId());
        TransactionResponse response = transactionService.updateTransaction(userPrincipal.getId(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an existing transaction")
    public ResponseEntity<Void> deleteTransaction(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @PathVariable Long id
    ) {
        log.info("Deleting transaction id {} for user id: {}", id, userPrincipal.getId());
        transactionService.deleteTransaction(userPrincipal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
