package com.sentinel.security;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window rate limiter for the login endpoint.
 * Keyed by client IP (honors X-Forwarded-For when behind a proxy).
 *
 * <p>Single-instance scope: SENTINEL runs one backend, so a distributed
 * limiter would be dead weight. If that ever changes, this is the seam
 * to replace.
 */
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final long windowSeconds;
    private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter() {
        this(5, 60);
    }

    LoginRateLimiter(int maxAttempts, long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    /** Returns false when the key has exhausted its attempt budget. */
    public synchronized boolean tryAcquire(String key) {
        Deque<Instant> times = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        Instant cutoff = Instant.now().minusSeconds(windowSeconds);
        while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
            times.pollFirst();
        }
        if (times.size() >= maxAttempts) {
            return false;
        }
        times.addLast(Instant.now());
        return true;
    }

    /** Visible for tests. */
    void clear() {
        attempts.clear();
    }
}
