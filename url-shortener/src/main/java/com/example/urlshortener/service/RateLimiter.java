package com.example.urlshortener.service;

import com.example.urlshortener.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {

    private final Map<String, Deque<Long>> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> linkCreationRequests = new ConcurrentHashMap<>();

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_WINDOW_MS = 10 * 60 * 1000L; // 10 minutes

    private static final int MAX_CREATION_REQUESTS = 60;
    private static final long CREATION_WINDOW_MS = 60 * 60 * 1000L; // 1 hour

    public void checkLoginRateLimit(String email) {
        if (email == null) return;
        String key = email.toLowerCase().trim();
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = loginAttempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            cleanOldTimestamps(timestamps, now - LOGIN_WINDOW_MS);
            if (timestamps.size() >= MAX_LOGIN_ATTEMPTS) {
                long oldest = timestamps.peekFirst();
                long retryAfterSeconds = Math.max(1, (oldest + LOGIN_WINDOW_MS - now) / 1000);
                throw new RateLimitExceededException("Too many failed login attempts. Please try again in " + retryAfterSeconds + " seconds.", retryAfterSeconds);
            }
        }
    }

    public void recordFailedLogin(String email) {
        if (email == null) return;
        String key = email.toLowerCase().trim();
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = loginAttempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            cleanOldTimestamps(timestamps, now - LOGIN_WINDOW_MS);
            timestamps.addLast(now);
        }
    }

    public void resetFailedLogin(String email) {
        if (email == null) return;
        loginAttempts.remove(email.toLowerCase().trim());
    }

    public void checkLinkCreationRateLimit(Long userId) {
        if (userId == null) return;
        String key = "user_" + userId;
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = linkCreationRequests.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            cleanOldTimestamps(timestamps, now - CREATION_WINDOW_MS);
            if (timestamps.size() >= MAX_CREATION_REQUESTS) {
                long oldest = timestamps.peekFirst();
                long retryAfterSeconds = Math.max(1, (oldest + CREATION_WINDOW_MS - now) / 1000);
                throw new RateLimitExceededException("Link creation rate limit exceeded (max 60 per hour). Please wait " + retryAfterSeconds + " seconds.", retryAfterSeconds);
            }
            timestamps.addLast(now);
        }
    }

    private void cleanOldTimestamps(Deque<Long> timestamps, long cutoff) {
        while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
            timestamps.pollFirst();
        }
    }
}
