package com.example.expensetracker.security;

import com.example.expensetracker.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private AppProperties appProperties;

    private JwtService jwtService;

    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        when(appProperties.getJwtSecret()).thenReturn(secret);
        jwtService = new JwtService(appProperties);
        userDetails = new User("test@example.com", "password", Collections.emptyList());
    }

    @Test
    @DisplayName("Should generate token and correctly extract subject and user ID")
    void testGenerateAndExtractClaims() {
        when(appProperties.getJwtExpirationMinutes()).thenReturn(60L);

        String token = jwtService.generateToken(userDetails, 42L, "INR");
        assertNotNull(token);

        String username = jwtService.extractUsername(token);
        assertEquals("test@example.com", username);

        Long userId = jwtService.extractUserId(token);
        assertEquals(42L, userId);

        assertTrue(jwtService.isTokenValid(token, userDetails));
        assertTrue(jwtService.validateToken(token));
    }

    @Test
    @DisplayName("Should fail validation when token is checked against different user")
    void testTokenInvalidForDifferentUser() {
        when(appProperties.getJwtExpirationMinutes()).thenReturn(60L);

        String token = jwtService.generateToken(userDetails, 1L, "INR");
        UserDetails otherUser = new User("other@example.com", "password", Collections.emptyList());

        assertFalse(jwtService.isTokenValid(token, otherUser));
    }

    @Test
    @DisplayName("Should fail validation on tampered token")
    void testTamperedTokenFails() {
        when(appProperties.getJwtExpirationMinutes()).thenReturn(60L);

        String token = jwtService.generateToken(userDetails, 1L, "INR");
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        assertFalse(jwtService.validateToken(tamperedToken));
    }

    @Test
    @DisplayName("Should fail validation on expired token")
    void testExpiredTokenFails() throws InterruptedException {
        // Token expiring in 1 ms
        String token = jwtService.generateTokenWithExpiration(userDetails, 1L, "INR", 1L);
        Thread.sleep(20);

        assertFalse(jwtService.validateToken(token));
        assertFalse(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    @DisplayName("Should return false on malformed token string")
    void testMalformedToken() {
        assertFalse(jwtService.validateToken("not.a.valid.jwt.token"));
        assertFalse(jwtService.validateToken(""));
    }
}
