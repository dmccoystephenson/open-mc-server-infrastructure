package com.openmc.webapp.service;

import com.openmc.webapp.config.ServerConfig;
import com.openmc.webapp.model.RetrievalRecord;
import com.openmc.webapp.repository.RetrievalRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RconService Max History Size Tests")
@ExtendWith(MockitoExtension.class)
class RconServiceMaxHistorySizeTest {

    private ServerConfig serverConfig;

    @Mock
    private RetrievalRecordRepository repository;

    private RconService rconService;
    private final List<RetrievalRecord> savedRecords = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        savedRecords.clear();
        serverConfig = new ServerConfig();
        serverConfig.setRefreshIntervalMs(1); // Very short interval for testing
        when(repository.findByTimestampAfterOrderByTimestampDesc(any(Instant.class)))
            .thenAnswer(invocation -> savedRecords.stream()
                .sorted(Comparator.comparing(RetrievalRecord::getTimestamp).reversed())
                .collect(Collectors.toList()));
        lenient().when(repository.save(any(RetrievalRecord.class)))
            .thenAnswer(invocation -> {
                savedRecords.add(invocation.getArgument(0));
                return invocation.getArgument(0);
            });
        rconService = new RconService(serverConfig, repository);
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

    @Test
    @DisplayName("Should show all 50 data points when slider is set to 50 with 50 records stored")
    void shouldShow50DataPointsWhenSliderSetTo50() throws InterruptedException {
        // Generate 50 data points
        for (int i = 0; i < 50; i++) {
            Thread.sleep(5); // Ensure refresh interval passes
            rconService.getServerStatus();
        }
        
        // Verify we have 10 (default max size) in memory
        assertEquals(10, rconService.getRetrievalHistory().size());
        
        // Set max size to 50
        rconService.setMaxHistorySize(50);
        
        // Should reload from repository and show all 50 items
        assertEquals(50, rconService.getRetrievalHistory().size(), 
            "Should show all 50 data points when slider is set to 50");
        
        // Verify the records are in correct order (most recent first)
        var history = rconService.getRetrievalHistory();
        for (int i = 0; i < history.size() - 1; i++) {
            assertTrue(history.get(i).getTimestamp().isAfter(history.get(i + 1).getTimestamp()) ||
                       history.get(i).getTimestamp().equals(history.get(i + 1).getTimestamp()),
                "Records should be ordered from most recent to oldest");
        }
    }
}
