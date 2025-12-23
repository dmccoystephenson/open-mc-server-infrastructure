package com.openmc.alertmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for rate limiting alerts to prevent flooding destinations
 */
@Service
@Slf4j
public class RateLimitService {

    @Value("${alert.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${alert.rate-limit.max-alerts:10}")
    private int maxAlerts;

    @Value("${alert.rate-limit.time-window-seconds:60}")
    private int timeWindowSeconds;

    // Track alert counts per destination
    private final Map<String, AlertWindow> alertWindows = new ConcurrentHashMap<>();

    /**
     * Check if an alert should be allowed based on rate limits
     *
     * @param destination The destination to check (e.g., "DISCORD", "MINECRAFT")
     * @return true if alert should be allowed, false if rate limited
     */
    public boolean shouldAllowAlert(String destination) {
        if (!rateLimitEnabled) {
            log.debug("Rate limiting is disabled, allowing alert to {}", destination);
            return true;
        }

        AlertWindow window = alertWindows.computeIfAbsent(destination, k -> new AlertWindow());
        
        synchronized (window) {
            Instant now = Instant.now();
            
            // Clean up old entries outside the time window
            window.removeOldEntries(now, timeWindowSeconds);
            
            // Check if we've exceeded the limit
            if (window.getCount() >= maxAlerts) {
                log.warn("Rate limit exceeded for destination: {}. Current count: {}, Max: {}, Window: {}s",
                         destination, window.getCount(), maxAlerts, timeWindowSeconds);
                return false;
            }
            
            // Add current alert to the window
            window.addAlert(now);
            log.debug("Alert allowed for destination: {}. Current count: {}/{} in {}s window",
                     destination, window.getCount(), maxAlerts, timeWindowSeconds);
            return true;
        }
    }

    /**
     * Reset rate limits for a specific destination (useful for testing)
     *
     * @param destination The destination to reset
     */
    public void resetRateLimit(String destination) {
        alertWindows.remove(destination);
        log.debug("Rate limit reset for destination: {}", destination);
    }

    /**
     * Reset all rate limits (useful for testing)
     */
    public void resetAllRateLimits() {
        alertWindows.clear();
        log.debug("All rate limits reset");
    }

    /**
     * Get the current alert count for a destination within the time window
     *
     * @param destination The destination to check
     * @return Current alert count in the time window
     */
    public int getCurrentCount(String destination) {
        AlertWindow window = alertWindows.get(destination);
        if (window == null) {
            return 0;
        }
        synchronized (window) {
            window.removeOldEntries(Instant.now(), timeWindowSeconds);
            return window.getCount();
        }
    }

    /**
     * Inner class to track alerts within a time window
     */
    private static class AlertWindow {
        private final java.util.List<Instant> timestamps = new java.util.ArrayList<>();

        public void addAlert(Instant timestamp) {
            timestamps.add(timestamp);
        }

        public int getCount() {
            return timestamps.size();
        }

        public void removeOldEntries(Instant now, int windowSeconds) {
            Instant cutoff = now.minusSeconds(windowSeconds);
            timestamps.removeIf(timestamp -> timestamp.isBefore(cutoff));
        }
    }
}
