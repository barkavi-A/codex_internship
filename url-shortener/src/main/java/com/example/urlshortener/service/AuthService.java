package com.example.urlshortener.service;

import com.example.urlshortener.dto.request.LoginRequest;
import com.example.urlshortener.dto.request.RegisterRequest;
import com.example.urlshortener.dto.response.AuthResponse;
import com.example.urlshortener.entity.User;
import com.example.urlshortener.exception.AliasConflictException;
import com.example.urlshortener.exception.BadRequestException;
import com.example.urlshortener.repository.UserRepository;
import com.example.urlshortener.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AliasConflictException("Email '" + request.email() + "' is already registered");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = new User(request.name(), request.email(), encodedPassword);
        User savedUser = userRepository.save(user);

        String token = jwtService.generateTokenForUser(savedUser.getId(), savedUser.getEmail(), savedUser.getName());
        return new AuthResponse(token, savedUser.getId(), savedUser.getEmail(), savedUser.getName());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        rateLimiter.checkLoginRateLimit(request.email());

        User user = userRepository.findByEmail(request.email())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            rateLimiter.recordFailedLogin(request.email());
            throw new BadCredentialsException("Invalid email or password");
        }

        rateLimiter.resetFailedLogin(request.email());
        String token = jwtService.generateTokenForUser(user.getId(), user.getEmail(), user.getName());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getName());
    }
}
