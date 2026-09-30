package com.example.expensetracker.security;

import com.example.expensetracker.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int WINDOW_MINUTES = 10;
    private static final int MAX_ENTRIES = 5000;

    private final ConcurrentHashMap<String, List<Instant>> failedAttempts = new ConcurrentHashMap<>();

    public void checkRateLimit(String email) {
        if (email == null) return;
        String key = email.toLowerCase().trim();
        List<Instant> attempts = failedAttempts.get(key);
        if (attempts == null) return;

        Instant threshold = Instant.now().minus(WINDOW_MINUTES, ChronoUnit.MINUTES);
        synchronized (attempts) {
            cleanOldAttempts(attempts, threshold);
            if (attempts.size() >= MAX_FAILED_ATTEMPTS) {
                throw new RateLimitExceededException(
                    "Too many failed login attempts for this account. Please try again in 10 minutes."
                );
            }
        }
    }

    public void recordFailedAttempt(String email) {
        if (email == null) return;
        String key = email.toLowerCase().trim();

        if (failedAttempts.size() > MAX_ENTRIES) {
            pruneStaleEntries();
        }

        List<Instant> attempts = failedAttempts.computeIfAbsent(key, k -> new ArrayList<>());
        Instant threshold = Instant.now().minus(WINDOW_MINUTES, ChronoUnit.MINUTES);
        synchronized (attempts) {
            cleanOldAttempts(attempts, threshold);
            attempts.add(Instant.now());
        }
    }

    public void resetAttempts(String email) {
        if (email == null) return;
        failedAttempts.remove(email.toLowerCase().trim());
    }

    private void cleanOldAttempts(List<Instant> attempts, Instant threshold) {
        Iterator<Instant> it = attempts.iterator();
        while (it.hasNext()) {
            if (it.next().isBefore(threshold)) {
                it.remove();
            }
        }
    }

    private void pruneStaleEntries() {
        Instant threshold = Instant.now().minus(WINDOW_MINUTES, ChronoUnit.MINUTES);
        failedAttempts.entrySet().removeIf(entry -> {
            List<Instant> list = entry.getValue();
            synchronized (list) {
                cleanOldAttempts(list, threshold);
                return list.isEmpty();
            }
        });
    }

    public void clear() {
        failedAttempts.clear();
    }
}
