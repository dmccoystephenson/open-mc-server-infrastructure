package com.openmc.alertmanager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "alert.rate-limit.enabled=true",
    "alert.rate-limit.max-alerts=10",
    "alert.rate-limit.time-window-seconds=3"
})
@DisplayName("RateLimitService Extended Tests")
class RateLimitServiceExtendedTest {

    @Autowired
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService.resetAllRateLimits();
    }

    @Test
    @DisplayName("Should handle concurrent access from multiple threads safely")
    void shouldHandleConcurrentAccessSafely() throws InterruptedException {
        String destination = "DISCORD";
        int threadCount = 10;
        int attemptsPerThread = 5;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);
        
        try {
            // Submit tasks that will all try to send alerts simultaneously
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < attemptsPerThread; j++) {
                            if (rateLimitService.shouldAllowAlert(destination)) {
                                allowedCount.incrementAndGet();
                            } else {
                                blockedCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            // Wait for all threads to complete
            assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete within timeout");
            
            // Verify that exactly 10 alerts were allowed (the limit)
            assertEquals(10, allowedCount.get(), "Exactly 10 alerts should be allowed");
            // The rest should be blocked (50 - 10 = 40)
            assertEquals(40, blockedCount.get(), "40 alerts should be blocked");
            // Verify count matches
            assertEquals(10, rateLimitService.getCurrentCount(destination));
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("Should handle sliding window correctly with partial expiration")
    void shouldHandleSlidingWindowWithPartialExpiration() throws InterruptedException {
        // Note: This test uses Thread.sleep to verify real-time sliding window behavior.
        // While a Clock abstraction would allow deterministic time control, this integration-style
        // test validates actual time-based expiration which is critical for rate limiting correctness.
        String destination = "DISCORD";
        
        // Send 5 alerts immediately
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimitService.shouldAllowAlert(destination));
        }
        assertEquals(5, rateLimitService.getCurrentCount(destination));
        
        // Wait 1.5 seconds
        Thread.sleep(1500);
        
        // Send 5 more alerts
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimitService.shouldAllowAlert(destination));
        }
        assertEquals(10, rateLimitService.getCurrentCount(destination));
        
        // Should be at limit now
        assertFalse(rateLimitService.shouldAllowAlert(destination));
        
        // Wait another 2 seconds (total 3.5s from first batch)
        // First 5 alerts should expire
        Thread.sleep(2000);
        
        // First 5 should have expired, so we should be able to send 5 more
        assertEquals(5, rateLimitService.getCurrentCount(destination));
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimitService.shouldAllowAlert(destination), 
                      "Alert " + (i + 1) + " should be allowed after first batch expired");
        }
        assertEquals(10, rateLimitService.getCurrentCount(destination));
    }

    @Test
    @DisplayName("Should allow exactly max alerts at boundary")
    void shouldAllowExactlyMaxAlertsAtBoundary() {
        String destination = "DISCORD";
        
        // Send exactly max alerts (10)
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimitService.shouldAllowAlert(destination),
                      "Alert " + (i + 1) + " should be allowed");
        }
        
        // The 11th should be blocked
        assertFalse(rateLimitService.shouldAllowAlert(destination),
                   "Alert 11 should be blocked at boundary");
        
        // Count should still be 10
        assertEquals(10, rateLimitService.getCurrentCount(destination));
    }

    @Test
    @DisplayName("Should handle multiple destinations with different usage patterns")
    void shouldHandleMultipleDestinationsWithDifferentPatterns() {
        String discord = "DISCORD";
        String minecraft = "MINECRAFT";
        String custom = "CUSTOM";
        
        // Fill Discord completely
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimitService.shouldAllowAlert(discord));
        }
        assertFalse(rateLimitService.shouldAllowAlert(discord));
        
        // Partially fill Minecraft
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimitService.shouldAllowAlert(minecraft));
        }
        assertTrue(rateLimitService.shouldAllowAlert(minecraft)); // Still allowed
        
        // Don't touch custom
        assertTrue(rateLimitService.shouldAllowAlert(custom));
        
        // Verify counts
        assertEquals(10, rateLimitService.getCurrentCount(discord));
        assertEquals(6, rateLimitService.getCurrentCount(minecraft));
        assertEquals(1, rateLimitService.getCurrentCount(custom));
    }

    @Test
    @DisplayName("Should handle rapid burst of alerts")
    void shouldHandleRapidBurstOfAlerts() {
        String destination = "DISCORD";
        int totalAttempts = 100;
        int allowedCount = 0;
        int blockedCount = 0;
        
        // Try to send 100 alerts as fast as possible
        for (int i = 0; i < totalAttempts; i++) {
            if (rateLimitService.shouldAllowAlert(destination)) {
                allowedCount++;
            } else {
                blockedCount++;
            }
        }
        
        // Should allow exactly 10 and block 90
        assertEquals(10, allowedCount, "Should allow exactly 10 alerts");
        assertEquals(90, blockedCount, "Should block 90 alerts");
        assertEquals(10, rateLimitService.getCurrentCount(destination));
    }

    @Test
    @DisplayName("Should correctly report count after partial window expiration")
    void shouldCorrectlyReportCountAfterPartialExpiration() throws InterruptedException {
        // Note: This test uses Thread.sleep to verify real-time window expiration behavior.
        // This validates that timestamps are correctly removed from the sliding window.
        String destination = "DISCORD";
        
        // Send 3 alerts
        for (int i = 0; i < 3; i++) {
            rateLimitService.shouldAllowAlert(destination);
        }
        assertEquals(3, rateLimitService.getCurrentCount(destination));
        
        // Wait for window to expire
        Thread.sleep(3500);
        
        // Count should be 0 now
        assertEquals(0, rateLimitService.getCurrentCount(destination));
        
        // Should be able to send fresh alerts
        assertTrue(rateLimitService.shouldAllowAlert(destination));
        assertEquals(1, rateLimitService.getCurrentCount(destination));
    }

    @Test
    @DisplayName("Should maintain accurate count when alternating between destinations")
    void shouldMaintainAccurateCountWhenAlternating() {
        String discord = "DISCORD";
        String minecraft = "MINECRAFT";
        
        // Alternate between destinations
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimitService.shouldAllowAlert(discord));
            assertTrue(rateLimitService.shouldAllowAlert(minecraft));
        }
        
        // Both should be at limit
        assertEquals(10, rateLimitService.getCurrentCount(discord));
        assertEquals(10, rateLimitService.getCurrentCount(minecraft));
        assertFalse(rateLimitService.shouldAllowAlert(discord));
        assertFalse(rateLimitService.shouldAllowAlert(minecraft));
    }

    @Test
    @DisplayName("Should handle reset during active rate limiting")
    void shouldHandleResetDuringActiveRateLimiting() {
        String destination = "DISCORD";
        
        // Fill to limit
        for (int i = 0; i < 10; i++) {
            rateLimitService.shouldAllowAlert(destination);
        }
        assertFalse(rateLimitService.shouldAllowAlert(destination));
        
        // Reset specific destination
        rateLimitService.resetRateLimit(destination);
        
        // Should be able to send again
        assertTrue(rateLimitService.shouldAllowAlert(destination));
        assertEquals(1, rateLimitService.getCurrentCount(destination));
    }

    @Test
    @DisplayName("Should handle resetAll with multiple active destinations")
    void shouldHandleResetAllWithMultipleActiveDestinations() {
        String discord = "DISCORD";
        String minecraft = "MINECRAFT";
        String custom = "CUSTOM";
        
        // Partially fill multiple destinations
        for (int i = 0; i < 5; i++) {
            rateLimitService.shouldAllowAlert(discord);
        }
        for (int i = 0; i < 8; i++) {
            rateLimitService.shouldAllowAlert(minecraft);
        }
        for (int i = 0; i < 3; i++) {
            rateLimitService.shouldAllowAlert(custom);
        }
        
        // Verify counts before reset
        assertEquals(5, rateLimitService.getCurrentCount(discord));
        assertEquals(8, rateLimitService.getCurrentCount(minecraft));
        assertEquals(3, rateLimitService.getCurrentCount(custom));
        
        // Reset all
        rateLimitService.resetAllRateLimits();
        
        // All counts should be 0
        assertEquals(0, rateLimitService.getCurrentCount(discord));
        assertEquals(0, rateLimitService.getCurrentCount(minecraft));
        assertEquals(0, rateLimitService.getCurrentCount(custom));
        
        // All should be able to send again
        assertTrue(rateLimitService.shouldAllowAlert(discord));
        assertTrue(rateLimitService.shouldAllowAlert(minecraft));
        assertTrue(rateLimitService.shouldAllowAlert(custom));
    }

    @Test
    @DisplayName("Should handle empty string as destination")
    void shouldHandleEmptyStringAsDestination() {
        String emptyDestination = "";
        
        // Empty string is valid (not null), should work normally
        assertTrue(rateLimitService.shouldAllowAlert(emptyDestination));
        assertEquals(1, rateLimitService.getCurrentCount(emptyDestination));
    }

    @Test
    @DisplayName("Should track different case destinations separately")
    void shouldTrackDifferentCaseDestinationsSeparately() {
        String upperCase = "DISCORD";
        String lowerCase = "discord";
        String mixedCase = "Discord";
        
        // Fill uppercase to limit
        for (int i = 0; i < 10; i++) {
            rateLimitService.shouldAllowAlert(upperCase);
        }
        
        // Different cases should be tracked separately
        assertTrue(rateLimitService.shouldAllowAlert(lowerCase));
        assertTrue(rateLimitService.shouldAllowAlert(mixedCase));
        
        assertEquals(10, rateLimitService.getCurrentCount(upperCase));
        assertEquals(1, rateLimitService.getCurrentCount(lowerCase));
        assertEquals(1, rateLimitService.getCurrentCount(mixedCase));
    }
}
