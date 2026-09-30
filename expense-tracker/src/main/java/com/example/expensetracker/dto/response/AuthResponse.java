package com.example.expensetracker.dto.response;

public record AuthResponse(
    String token,
    String tokenType,
    Long userId,
    String name,
    String email,
    String currency
) {
    public static AuthResponse of(String token, Long userId, String name, String email, String currency) {
        return new AuthResponse(token, "Bearer", userId, name, email, currency);
    }
}
