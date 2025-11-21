package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.repository.InMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ActivityTrackerService Max History Size Tests")
class ActivityTrackerServiceMaxHistorySizeTest {

    private ServerConfig serverConfig;
    private ActivityTrackerService activityTrackerService;

    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
        serverConfig.setActivityTrackerEnabled(false); // Disable to prevent actual API calls
        activityTrackerService = new ActivityTrackerService(serverConfig, new InMemoryRepository<>());
    }

    @Test
    @DisplayName("Should have default max history size of 10")
    void shouldHaveDefaultMaxHistorySize() {
        assertEquals(10, activityTrackerService.getMaxHistorySize());
    }

    @Test
    @DisplayName("Should allow setting max history size")
    void shouldAllowSettingMaxHistorySize() {
        activityTrackerService.setMaxHistorySize(20);
        assertEquals(20, activityTrackerService.getMaxHistorySize());
    }

    @Test
    @DisplayName("Should reject max history size of 0")
    void shouldRejectMaxHistorySizeOfZero() {
        assertThrows(IllegalArgumentException.class, () -> {
            activityTrackerService.setMaxHistorySize(0);
        });
    }

    @Test
    @DisplayName("Should reject negative max history size")
    void shouldRejectNegativeMaxHistorySize() {
        assertThrows(IllegalArgumentException.class, () -> {
            activityTrackerService.setMaxHistorySize(-5);
        });
    }
}
