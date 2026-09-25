package com.delfino.expensetracker.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PublicShareRateLimitService {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Value("${app.sharing.public-rate-limit-per-minute:30}")
    private int maxRequestsPerMinute;

    public boolean allow(HttpServletRequest request) {
        String key = resolveClientIp(request);
        Instant now = Instant.now();
        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStart().plus(WINDOW).isBefore(now)) {
                return new WindowCounter(now, 1);
            }
            return new WindowCounter(existing.windowStart(), existing.count() + 1);
        });
        cleanupExpired(now);
        return counter.count() <= Math.max(1, maxRequestsPerMinute);
    }

    public void clear() {
        counters.clear();
    }

    private void cleanupExpired(Instant now) {
        counters.entrySet().removeIf(entry -> entry.getValue().windowStart().plus(WINDOW.multipliedBy(2)).isBefore(now));
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    record WindowCounter(Instant windowStart, int count) {
    }
}


