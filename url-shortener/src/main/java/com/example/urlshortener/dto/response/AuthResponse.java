package com.example.urlshortener.dto.response;

public record AuthResponse(
        String token,
        String tokenType,
        Long userId,
        String email,
        String name
) {
    public AuthResponse(String token, Long userId, String email, String name) {
        this(token, "Bearer", userId, email, name);
    }
}
