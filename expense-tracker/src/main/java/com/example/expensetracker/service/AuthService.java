package com.example.expensetracker.service;

import com.example.expensetracker.dto.request.LoginRequest;
import com.example.expensetracker.dto.request.RegisterRequest;
import com.example.expensetracker.dto.response.AuthResponse;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.exception.ConflictException;
import com.example.expensetracker.repository.UserRepository;
import com.example.expensetracker.security.JwtService;
import com.example.expensetracker.security.LoginRateLimiter;
import com.example.expensetracker.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing user authentication, registration, and token generation.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtService jwtService,
        LoginRateLimiter rateLimiter
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Registers a new user with hashed password and returns an auth response with JWT.
     *
     * @param request registration details
     * @return AuthResponse containing token and user profile
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with email '" + email + "' already exists");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCurrency(request.currency() != null && !request.currency().isBlank() ? request.currency().trim() : "INR");

        User savedUser = userRepository.save(user);
        log.info("Registered new user with id {} and email {}", savedUser.getId(), savedUser.getEmail());

        UserPrincipal principal = UserPrincipal.create(savedUser);
        String token = jwtService.generateToken(principal, savedUser.getId(), savedUser.getCurrency());

        return AuthResponse.of(token, savedUser.getId(), savedUser.getName(), savedUser.getEmail(), savedUser.getCurrency());
    }

    /**
     * Authenticates a user against credentials with rate-limiting protection.
     *
     * @param request login credentials
     * @return AuthResponse containing token and user profile
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase().trim();

        // 1. Check rate limiter before attempting authentication
        rateLimiter.checkRateLimit(email);

        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
            );

            // 2. Reset rate limiter on successful authentication
            rateLimiter.resetAttempts(email);

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            String token = jwtService.generateToken(principal, principal.getId(), principal.getCurrency());

            log.info("Successful login for user id: {}", principal.getId());
            return AuthResponse.of(token, principal.getId(), principal.getName(), principal.getUsername(), principal.getCurrency());
        } catch (BadCredentialsException ex) {
            // 3. Record failed attempt for rate limiting
            rateLimiter.recordFailedAttempt(email);
            log.warn("Failed login attempt for email: {}", email);
            throw new BadCredentialsException("Invalid email or password");
        }
    }
}
