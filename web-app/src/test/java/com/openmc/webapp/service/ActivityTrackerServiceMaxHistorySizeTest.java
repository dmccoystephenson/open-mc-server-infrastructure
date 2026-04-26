package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.mapper.PlayerProfileMapper;
import com.openmc.webapp.repository.InMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityTrackerService Max History Size Tests")
class ActivityTrackerServiceMaxHistorySizeTest {

    private ServerConfig serverConfig;
    private ActivityTrackerService activityTrackerService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private PlayerProfileMapper playerProfileMapper;

    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
        serverConfig.setActivityTrackerEnabled(false); // Disable to prevent actual API calls
        activityTrackerService = new ActivityTrackerService(serverConfig, new InMemoryRepository<>(), restTemplate, playerProfileMapper);
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
