package com.openmc.alertmanager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "alert.rate-limit.enabled=true",
    "alert.rate-limit.max-alerts=5",
    "alert.rate-limit.time-window-seconds=2"
})
@DisplayName("RateLimitService Tests")
class RateLimitServiceTest {

    @Autowired
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService.resetAllRateLimits();
    }

    @Test
    @DisplayName("Should allow alerts within rate limit")
    void shouldAllowAlertsWithinRateLimit() {
        String destination = "DISCORD";
        
        // Should allow first 5 alerts
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimitService.shouldAllowAlert(destination),
                      "Alert " + (i + 1) + " should be allowed");
        }
        
        assertEquals(5, rateLimitService.getCurrentCount(destination));
    }

    @Test
    @DisplayName("Should block alerts when rate limit exceeded")
    void shouldBlockAlertsWhenRateLimitExceeded() {
        String destination = "DISCORD";
        
        // Fill up to the limit
        for (int i = 0; i < 5; i++) {
            rateLimitService.shouldAllowAlert(destination);
        }
        
        // 6th alert should be blocked
        assertFalse(rateLimitService.shouldAllowAlert(destination),
                   "6th alert should be blocked");
        
        // Still should be at 5 because the 6th was blocked
        assertEquals(5, rateLimitService.getCurrentCount(destination));
    }

    @Test
    @DisplayName("Should allow alerts after time window expires")
    void shouldAllowAlertsAfterTimeWindowExpires() throws InterruptedException {
        String destination = "DISCORD";
        
        // Fill up to the limit
        for (int i = 0; i < 5; i++) {
            rateLimitService.shouldAllowAlert(destination);
        }
        
        // Should be blocked
        assertFalse(rateLimitService.shouldAllowAlert(destination));
        
        // Wait for time window to expire (2 seconds + buffer)
        Thread.sleep(2500);
        
        // Should be allowed again after window expires
        assertTrue(rateLimitService.shouldAllowAlert(destination),
                  "Alert should be allowed after time window expires");
    }

    @Test
    @DisplayName("Should track rate limits independently per destination")
    void shouldTrackRateLimitsIndependentlyPerDestination() {
        String discord = "DISCORD";
        String minecraft = "MINECRAFT";
        
        // Fill Discord to the limit
        for (int i = 0; i < 5; i++) {
            rateLimitService.shouldAllowAlert(discord);
        }
        
        // Discord should be blocked
        assertFalse(rateLimitService.shouldAllowAlert(discord));
        
        // Minecraft should still be allowed
        assertTrue(rateLimitService.shouldAllowAlert(minecraft));
        
        assertEquals(5, rateLimitService.getCurrentCount(discord));
        assertEquals(1, rateLimitService.getCurrentCount(minecraft));
    }

    @Test
    @DisplayName("Should reset rate limit for specific destination")
    void shouldResetRateLimitForSpecificDestination() {
        String destination = "DISCORD";
        
        // Fill up to the limit
        for (int i = 0; i < 5; i++) {
            rateLimitService.shouldAllowAlert(destination);
        }
        
        // Should be blocked
        assertFalse(rateLimitService.shouldAllowAlert(destination));
        
        // Reset rate limit
        rateLimitService.resetRateLimit(destination);
        
        // Should be allowed again
        assertTrue(rateLimitService.shouldAllowAlert(destination));
        assertEquals(1, rateLimitService.getCurrentCount(destination));
    }

    @Test
    @DisplayName("Should reset all rate limits")
    void shouldResetAllRateLimits() {
        String discord = "DISCORD";
        String minecraft = "MINECRAFT";
        
        // Fill both to the limit
        for (int i = 0; i < 5; i++) {
            rateLimitService.shouldAllowAlert(discord);
            rateLimitService.shouldAllowAlert(minecraft);
        }
        
        // Both should be blocked
        assertFalse(rateLimitService.shouldAllowAlert(discord));
        assertFalse(rateLimitService.shouldAllowAlert(minecraft));
        
        // Reset all
        rateLimitService.resetAllRateLimits();
        
        // Both should be allowed again
        assertTrue(rateLimitService.shouldAllowAlert(discord));
        assertTrue(rateLimitService.shouldAllowAlert(minecraft));
    }

    @Test
    @DisplayName("Should return zero count for unknown destination")
    void shouldReturnZeroCountForUnknownDestination() {
        assertEquals(0, rateLimitService.getCurrentCount("UNKNOWN"));
    }
}
