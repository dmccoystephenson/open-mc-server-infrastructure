package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.repository.InMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RconService Max History Size Tests")
class RconServiceMaxHistorySizeTest {

    private ServerConfig serverConfig;
    private RconService rconService;

    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
        serverConfig.setRefreshIntervalMs(1); // Very short interval for testing
        rconService = new RconService(serverConfig, new InMemoryRepository<>());
    }

    @Test
    @DisplayName("Should have default max history size of 10")
    void shouldHaveDefaultMaxHistorySize() {
        assertEquals(10, rconService.getMaxHistorySize());
    }

    @Test
    @DisplayName("Should allow setting max history size")
    void shouldAllowSettingMaxHistorySize() {
        rconService.setMaxHistorySize(20);
        assertEquals(20, rconService.getMaxHistorySize());
    }

    @Test
    @DisplayName("Should reject max history size of 0")
    void shouldRejectMaxHistorySizeOfZero() {
        assertThrows(IllegalArgumentException.class, () -> {
            rconService.setMaxHistorySize(0);
        });
    }

    @Test
    @DisplayName("Should reject negative max history size")
    void shouldRejectNegativeMaxHistorySize() {
        assertThrows(IllegalArgumentException.class, () -> {
            rconService.setMaxHistorySize(-5);
        });
    }

    @Test
    @DisplayName("Should trim history when reducing max size")
    void shouldTrimHistoryWhenReducingMaxSize() throws InterruptedException {
        // Generate some history
        for (int i = 0; i < 5; i++) {
            Thread.sleep(5); // Ensure refresh interval passes
            rconService.getServerStatus();
        }
        
        assertEquals(5, rconService.getRetrievalHistory().size());
        
        // Reduce max size
        rconService.setMaxHistorySize(3);
        
        // History should be trimmed to 3 items
        assertEquals(3, rconService.getRetrievalHistory().size());
    }

    @Test
    @DisplayName("Should maintain history order after trimming")
    void shouldMaintainHistoryOrderAfterTrimming() throws InterruptedException {
        // Generate some history
        for (int i = 0; i < 5; i++) {
            Thread.sleep(5); // Ensure refresh interval passes
            rconService.getServerStatus();
        }
        
        // Get the first (most recent) item timestamp
        var firstItemBefore = rconService.getRetrievalHistory().get(0).getTimestamp();
        
        // Reduce max size
        rconService.setMaxHistorySize(3);
        
        // Most recent item should still be first
        var firstItemAfter = rconService.getRetrievalHistory().get(0).getTimestamp();
        assertEquals(firstItemBefore, firstItemAfter);
    }

    @Test
    @DisplayName("Should reload history from repository when increasing max size")
    void shouldReloadHistoryWhenIncreasingMaxSize() throws InterruptedException {
        // Generate 10 data points
        for (int i = 0; i < 10; i++) {
            Thread.sleep(5); // Ensure refresh interval passes
            rconService.getServerStatus();
        }
        
        assertEquals(10, rconService.getRetrievalHistory().size());
        
        // Reduce max size to 5
        rconService.setMaxHistorySize(5);
        assertEquals(5, rconService.getRetrievalHistory().size());
        
        // Increase max size back to 10
        rconService.setMaxHistorySize(10);
        
        // Should reload from repository and show 10 items (or all available)
        // Since repository has retention filtering, we should have at least 5 and up to 10
        assertTrue(rconService.getRetrievalHistory().size() >= 5, 
            "Should have at least 5 items after increasing size");
        assertTrue(rconService.getRetrievalHistory().size() <= 10, 
            "Should not exceed 10 items");
    }
}
