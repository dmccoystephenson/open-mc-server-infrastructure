package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.model.ActivityTrackerStats;
import com.openmc.webapp.model.LeaderboardEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ActivityTrackerService Tests")
class ActivityTrackerServiceTest {

    private ServerConfig serverConfig;
    private ActivityTrackerService activityTrackerService;

    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
        activityTrackerService = new ActivityTrackerService(serverConfig);
    }

    @Test
    @DisplayName("Should be disabled when URL is not configured")
    void shouldBeDisabledWhenUrlNotConfigured() {
        serverConfig.setActivityTrackerEnabled(true);
        serverConfig.setActivityTrackerUrl("");
        
        assertFalse(activityTrackerService.isEnabled());
    }

    @Test
    @DisplayName("Should be disabled when flag is false")
    void shouldBeDisabledWhenFlagIsFalse() {
        serverConfig.setActivityTrackerEnabled(false);
        serverConfig.setActivityTrackerUrl("http://localhost:8080");
        
        assertFalse(activityTrackerService.isEnabled());
    }

    @Test
    @DisplayName("Should be enabled when URL is configured and flag is true")
    void shouldBeEnabledWhenConfigured() {
        serverConfig.setActivityTrackerEnabled(true);
        serverConfig.setActivityTrackerUrl("http://localhost:8080");
        
        assertTrue(activityTrackerService.isEnabled());
    }

    @Test
    @DisplayName("Should return null stats when disabled")
    void shouldReturnNullStatsWhenDisabled() {
        serverConfig.setActivityTrackerEnabled(false);
        
        ActivityTrackerStats stats = activityTrackerService.getStats();
        
        assertNull(stats);
    }

    @Test
    @DisplayName("Should return empty leaderboard when disabled")
    void shouldReturnEmptyLeaderboardWhenDisabled() {
        serverConfig.setActivityTrackerEnabled(false);
        
        List<LeaderboardEntry> leaderboard = activityTrackerService.getLeaderboard();
        
        assertNotNull(leaderboard);
        assertTrue(leaderboard.isEmpty());
    }

    @Test
    @DisplayName("Should return false for health check when disabled")
    void shouldReturnFalseForHealthCheckWhenDisabled() {
        serverConfig.setActivityTrackerEnabled(false);
        
        assertFalse(activityTrackerService.isHealthy());
    }

    @Test
    @DisplayName("Should handle connection errors gracefully")
    void shouldHandleConnectionErrorsGracefully() {
        serverConfig.setActivityTrackerEnabled(true);
        serverConfig.setActivityTrackerUrl("http://invalid-host:9999");
        
        // Should not throw exceptions
        assertDoesNotThrow(() -> {
            ActivityTrackerStats stats = activityTrackerService.getStats();
            assertNull(stats);
        });
        
        assertDoesNotThrow(() -> {
            List<LeaderboardEntry> leaderboard = activityTrackerService.getLeaderboard();
            assertTrue(leaderboard.isEmpty());
        });
        
        assertDoesNotThrow(() -> {
            boolean healthy = activityTrackerService.isHealthy();
            assertFalse(healthy);
        });
    }
}
