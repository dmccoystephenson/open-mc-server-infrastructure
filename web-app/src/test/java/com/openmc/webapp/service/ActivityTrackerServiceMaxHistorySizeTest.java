package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.repository.ActivityTrackerSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ActivityTrackerService Max History Size Tests")
@ExtendWith(MockitoExtension.class)
class ActivityTrackerServiceMaxHistorySizeTest {

    private ServerConfig serverConfig;

    @Mock
    private ActivityTrackerSnapshotRepository repository;

    private ActivityTrackerService activityTrackerService;

    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
        serverConfig.setActivityTrackerEnabled(false); // Disable to prevent actual API calls
        when(repository.findByTimestampAfterOrderByTimestampDesc(any(Instant.class)))
            .thenReturn(Collections.emptyList());
        activityTrackerService = new ActivityTrackerService(serverConfig, repository);
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
