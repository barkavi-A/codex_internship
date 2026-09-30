package com.example.expensetracker;

import com.example.expensetracker.dto.request.BudgetRequest;
import com.example.expensetracker.dto.request.CategoryRequest;
import com.example.expensetracker.dto.request.RegisterRequest;
import com.example.expensetracker.dto.request.TransactionRequest;
import com.example.expensetracker.dto.response.AuthResponse;
import com.example.expensetracker.dto.response.BudgetResponse;
import com.example.expensetracker.dto.response.CategoryResponse;
import com.example.expensetracker.dto.response.TransactionResponse;
import com.example.expensetracker.entity.Category;
import com.example.expensetracker.enums.PaymentMethod;
import com.example.expensetracker.enums.TransactionType;
import com.example.expensetracker.repository.CategoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    private String tokenUserA;
    private String tokenUserB;

    private Long transactionIdUserA;
    private Long categoryIdUserA;
    private Long budgetIdUserA;
    private Long globalCategoryId;

    @BeforeEach
    void setUp() throws Exception {
        long ts = System.currentTimeMillis();
        // Register User A
        RegisterRequest reqA = new RegisterRequest("User A", "user_a_" + ts + "@test.com", "Password123", "INR");
        MvcResult resA = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqA)))
            .andExpect(status().isCreated())
            .andReturn();
        tokenUserA = "Bearer " + objectMapper.readValue(resA.getResponse().getContentAsString(), AuthResponse.class).token();

        // Register User B
        RegisterRequest reqB = new RegisterRequest("User B", "user_b_" + ts + "@test.com", "Password123", "INR");
        MvcResult resB = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqB)))
            .andExpect(status().isCreated())
            .andReturn();
        tokenUserB = "Bearer " + objectMapper.readValue(resB.getResponse().getContentAsString(), AuthResponse.class).token();

        // User A creates Category
        CategoryRequest catReq = new CategoryRequest("Private Cat A", TransactionType.EXPENSE, "#112233", "lock");
        MvcResult catRes = mockMvc.perform(post("/api/categories")
                .header("Authorization", tokenUserA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(catReq)))
            .andExpect(status().isCreated())
            .andReturn();
        categoryIdUserA = objectMapper.readValue(catRes.getResponse().getContentAsString(), CategoryResponse.class).id();

        // User A creates Transaction
        TransactionRequest txReq = new TransactionRequest(
            categoryIdUserA, TransactionType.EXPENSE, BigDecimal.valueOf(500.00),
            "Private Tx A", LocalDate.now(), PaymentMethod.CARD, null
        );
        MvcResult txRes = mockMvc.perform(post("/api/transactions")
                .header("Authorization", tokenUserA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(txReq)))
            .andExpect(status().isCreated())
            .andReturn();
        transactionIdUserA = objectMapper.readValue(txRes.getResponse().getContentAsString(), TransactionResponse.class).id();

        // User A creates Budget
        BudgetRequest budReq = new BudgetRequest(categoryIdUserA, "2026-10", BigDecimal.valueOf(3000.00));
        MvcResult budRes = mockMvc.perform(post("/api/budgets")
                .header("Authorization", tokenUserA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(budReq)))
            .andExpect(status().isCreated())
            .andReturn();
        budgetIdUserA = objectMapper.readValue(budRes.getResponse().getContentAsString(), BudgetResponse.class).id();

        // Find a global category
        Category globalCat = categoryRepository.findAll().stream()
            .filter(Category::isGlobal)
            .findFirst()
            .orElseThrow();
        globalCategoryId = globalCat.getId();
    }

    @Test
    @DisplayName("User B cannot read, update, or delete User A's transaction (returns 404, not 403)")
    void testTransactionCrossUserIsolation() throws Exception {
        // Read -> 404
        mockMvc.perform(get("/api/transactions/" + transactionIdUserA)
                .header("Authorization", tokenUserB))
            .andExpect(status().isNotFound());

        // Update -> 404
        TransactionRequest updateReq = new TransactionRequest(
            globalCategoryId, TransactionType.EXPENSE, BigDecimal.valueOf(100.00),
            "Hack", LocalDate.now(), PaymentMethod.CASH, null
        );
        mockMvc.perform(put("/api/transactions/" + transactionIdUserA)
                .header("Authorization", tokenUserB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isNotFound());

        // Delete -> 404
        mockMvc.perform(delete("/api/transactions/" + transactionIdUserA)
                .header("Authorization", tokenUserB))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("User B cannot update or delete User A's category (returns 404)")
    void testCategoryCrossUserIsolation() throws Exception {
        CategoryRequest updateReq = new CategoryRequest("Hacked Cat", TransactionType.EXPENSE, "#000", "hack");

        mockMvc.perform(put("/api/categories/" + categoryIdUserA)
                .header("Authorization", tokenUserB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/categories/" + categoryIdUserA)
                .header("Authorization", tokenUserB))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("User B cannot read, update, or delete User A's budget (returns 404)")
    void testBudgetCrossUserIsolation() throws Exception {
        mockMvc.perform(get("/api/budgets/" + budgetIdUserA)
                .header("Authorization", tokenUserB))
            .andExpect(status().isNotFound());

        BudgetRequest updateReq = new BudgetRequest(null, "2026-10", BigDecimal.valueOf(1000.00));
        mockMvc.perform(put("/api/budgets/" + budgetIdUserA)
                .header("Authorization", tokenUserB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/budgets/" + budgetIdUserA)
                .header("Authorization", tokenUserB))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Global categories cannot be modified or deleted (returns 409 Conflict)")
    void testGlobalCategoriesCannotBeModifiedOrDeleted() throws Exception {
        CategoryRequest updateReq = new CategoryRequest("Changed Name", TransactionType.EXPENSE, "#000", "icon");

        // Try updating global category
        mockMvc.perform(put("/api/categories/" + globalCategoryId)
                .header("Authorization", tokenUserA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isConflict());

        // Try deleting global category
        mockMvc.perform(delete("/api/categories/" + globalCategoryId)
                .header("Authorization", tokenUserA))
            .andExpect(status().isConflict());
    }
}
