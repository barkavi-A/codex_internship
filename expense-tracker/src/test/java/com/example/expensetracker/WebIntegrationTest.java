package com.example.expensetracker;

import com.example.expensetracker.dto.request.*;
import com.example.expensetracker.dto.response.AuthResponse;
import com.example.expensetracker.dto.response.CategoryResponse;
import com.example.expensetracker.dto.response.TransactionResponse;
import com.example.expensetracker.enums.PaymentMethod;
import com.example.expensetracker.enums.TransactionType;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Complete User Lifecycle Flow: Register -> Login -> Create Category -> Create Transaction -> List -> Report")
    void testCompleteUserLifecycle() throws Exception {
        String email = "lifecycle_" + System.currentTimeMillis() + "@test.com";

        // 1. Register
        RegisterRequest registerReq = new RegisterRequest("Lifecycle User", email, "Password123", "INR");
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.email").value(email))
            .andReturn();

        // 2. Login
        LoginRequest loginReq = new LoginRequest(email, "Password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn();

        AuthResponse auth = objectMapper.readValue(loginResult.getResponse().getContentAsString(), AuthResponse.class);
        String token = "Bearer " + auth.token();

        // 3. Create Custom Category
        CategoryRequest catReq = new CategoryRequest("Gym Membership", TransactionType.EXPENSE, "#10B981", "dumbbell");
        MvcResult catResult = mockMvc.perform(post("/api/categories")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(catReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Gym Membership"))
            .andReturn();

        CategoryResponse cat = objectMapper.readValue(catResult.getResponse().getContentAsString(), CategoryResponse.class);

        // 4. Create Transaction
        TransactionRequest txReq = new TransactionRequest(
            cat.id(), TransactionType.EXPENSE, BigDecimal.valueOf(2500.00),
            "Annual Membership Fee", LocalDate.now(), PaymentMethod.CARD, "Fitness"
        );

        MvcResult txResult = mockMvc.perform(post("/api/transactions")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(txReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount").value(2500.00))
            .andExpect(jsonPath("$.categoryName").value("Gym Membership"))
            .andReturn();

        TransactionResponse tx = objectMapper.readValue(txResult.getResponse().getContentAsString(), TransactionResponse.class);

        // 5. List Transactions with Filter
        mockMvc.perform(get("/api/transactions")
                .header("Authorization", token)
                .param("search", "Annual")
                .param("type", "EXPENSE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].id").value(tx.id()));

        // 6. Get Summary Report
        mockMvc.perform(get("/api/reports/summary")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalExpense").value(2500.00))
            .andExpect(jsonPath("$.transactionCount").value(1));
    }

    @Test
    @DisplayName("Validation failure on registration returns 400 with standardized fieldErrors")
    void testRegisterValidationErrorsReturn400() throws Exception {
        RegisterRequest invalidReq = new RegisterRequest("", "invalid-email", "short", "INR");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.fieldErrors").isArray())
            .andExpect(jsonPath("$.fieldErrors", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401 Unauthorized")
    void testUnauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/transactions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Login endpoint rate limits after consecutive failures returning 429")
    void testLoginRateLimiting() throws Exception {
        String testEmail = "ratelimit_" + System.currentTimeMillis() + "@test.com";
        LoginRequest badCredentials = new LoginRequest(testEmail, "WrongPassword123");

        // Attempt 1..5: returns 401
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(badCredentials)))
                .andExpect(status().isUnauthorized());
        }

        // Attempt 6: rate limit exceeded -> 429
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badCredentials)))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.message").value(containsString("Too many failed login attempts")));
    }
}
