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
    "alert.rate-limit.enabled=false",
    "alert.rate-limit.max-alerts=5",
    "alert.rate-limit.time-window-seconds=2"
})
@DisplayName("RateLimitService Disabled Tests")
class RateLimitServiceDisabledTest {

    @Autowired
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService.resetAllRateLimits();
    }

    @Test
    @DisplayName("Should allow all alerts when rate limiting is disabled")
    void shouldAllowAllAlertsWhenRateLimitingIsDisabled() {
        String destination = "DISCORD";
        
        // Should allow far more than the configured max (5) since rate limiting is disabled
        for (int i = 0; i < 20; i++) {
            assertTrue(rateLimitService.shouldAllowAlert(destination),
                      "Alert " + (i + 1) + " should be allowed when rate limiting is disabled");
        }
    }

    @Test
    @DisplayName("Should allow alerts for all destinations when rate limiting is disabled")
    void shouldAllowAlertsForAllDestinationsWhenRateLimitingIsDisabled() {
        String discord = "DISCORD";
        String minecraft = "MINECRAFT";
        
        // Should allow many alerts for all destinations
        for (int i = 0; i < 15; i++) {
            assertTrue(rateLimitService.shouldAllowAlert(discord));
            assertTrue(rateLimitService.shouldAllowAlert(minecraft));
        }
    }
}
