package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.mapper.PlayerProfileMapper;
import com.openmc.webapp.model.ActivityTrackerStats;
import com.openmc.webapp.model.LeaderboardEntry;
import com.openmc.webapp.repository.InMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityTrackerService Tests")
class ActivityTrackerServiceTest {

    private ServerConfig serverConfig;
    private ActivityTrackerService activityTrackerService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private PlayerProfileMapper playerProfileMapper;

    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
        activityTrackerService = new ActivityTrackerService(serverConfig, new InMemoryRepository<>(), restTemplate, playerProfileMapper);
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

        doThrow(new RuntimeException("Connection refused"))
                .when(restTemplate).getForObject(anyString(), any(Class.class));

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
    
    @Test
    @DisplayName("Should return null player profile when disabled")
    void shouldReturnNullPlayerProfileWhenDisabled() {
        serverConfig.setActivityTrackerEnabled(false);
        
        com.openmc.webapp.model.PlayerProfile profile = activityTrackerService.getPlayerProfile("TestPlayer");
        
        assertNull(profile);
    }
    
    @Test
    @DisplayName("Should return null player profile for null player name")
    void shouldReturnNullForNullPlayerName() {
        serverConfig.setActivityTrackerEnabled(true);
        serverConfig.setActivityTrackerUrl("http://localhost:8080");
        
        com.openmc.webapp.model.PlayerProfile profile = activityTrackerService.getPlayerProfile(null);
        
        assertNull(profile);
    }
    
    @Test
    @DisplayName("Should return null player profile for empty player name")
    void shouldReturnNullForEmptyPlayerName() {
        serverConfig.setActivityTrackerEnabled(true);
        serverConfig.setActivityTrackerUrl("http://localhost:8080");
        
        com.openmc.webapp.model.PlayerProfile profile = activityTrackerService.getPlayerProfile("");
        
        assertNull(profile);
    }
}
