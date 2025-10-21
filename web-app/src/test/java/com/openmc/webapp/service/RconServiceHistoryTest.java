package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.model.RetrievalRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RconService History Tests")
class RconServiceHistoryTest {

    private ServerConfig serverConfig;
    private RconService rconService;

    @BeforeEach
    void setUp() {
        serverConfig = new ServerConfig();
        rconService = new RconService(serverConfig);
    }

    @Test
    @DisplayName("Should initialize with empty history")
    void shouldInitializeWithEmptyHistory() {
        List<RetrievalRecord> history = rconService.getRetrievalHistory();
        assertNotNull(history);
        assertEquals(0, history.size());
    }

    @Test
    @DisplayName("Should add retrieval record to history when getting server status")
    void shouldAddRetrievalRecordToHistoryWhenGettingServerStatus() {
        rconService.getServerStatus();
        
        List<RetrievalRecord> history = rconService.getRetrievalHistory();
        assertEquals(1, history.size());
        
        RetrievalRecord record = history.get(0);
        assertNotNull(record.getTimestamp());
        assertNotNull(record.getPlayerList());
    }

    @Test
    @DisplayName("Should limit history to 10 entries")
    void shouldLimitHistoryToTenEntries() {
        // Set a very short refresh interval
        serverConfig.setRefreshIntervalMs(1);
        
        // Retrieve status 15 times
        for (int i = 0; i < 15; i++) {
            try {
                Thread.sleep(2); // Ensure refresh interval passes
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            rconService.getServerStatus();
        }
        
        List<RetrievalRecord> history = rconService.getRetrievalHistory();
        assertEquals(10, history.size());
    }

    @Test
    @DisplayName("Should keep most recent entries when history exceeds limit")
    void shouldKeepMostRecentEntriesWhenHistoryExceedsLimit() {
        // Set a very short refresh interval
        serverConfig.setRefreshIntervalMs(1);
        
        // Retrieve status multiple times
        for (int i = 0; i < 15; i++) {
            try {
                Thread.sleep(2); // Ensure refresh interval passes
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            rconService.getServerStatus();
        }
        
        List<RetrievalRecord> history = rconService.getRetrievalHistory();
        
        // Verify history size is limited
        assertEquals(10, history.size());
        
        // Verify most recent entry is first
        RetrievalRecord mostRecent = history.get(0);
        RetrievalRecord oldest = history.get(9);
        assertTrue(mostRecent.getTimestamp().isAfter(oldest.getTimestamp()));
    }

    @Test
    @DisplayName("Should return unmodifiable list for history")
    void shouldReturnUnmodifiableListForHistory() {
        rconService.getServerStatus();
        
        List<RetrievalRecord> history = rconService.getRetrievalHistory();
        
        assertThrows(UnsupportedOperationException.class, () -> {
            history.clear();
        });
    }

    @Test
    @DisplayName("Retrieval record should have correct success status when offline")
    void retrievalRecordShouldHaveCorrectSuccessStatusWhenOffline() {
        rconService.getServerStatus(); // Will fail to connect in test
        
        List<RetrievalRecord> history = rconService.getRetrievalHistory();
        assertEquals(1, history.size());
        
        RetrievalRecord record = history.get(0);
        assertFalse(record.isSuccess()); // Should be false since server is not running
    }

    @Test
    @DisplayName("Should track player count in retrieval record")
    void shouldTrackPlayerCountInRetrievalRecord() {
        rconService.getServerStatus();
        
        List<RetrievalRecord> history = rconService.getRetrievalHistory();
        assertEquals(1, history.size());
        
        RetrievalRecord record = history.get(0);
        assertTrue(record.getPlayerCount() >= 0); // Should be 0 or positive
    }
}
