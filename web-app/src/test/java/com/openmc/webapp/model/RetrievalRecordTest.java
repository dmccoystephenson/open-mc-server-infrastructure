package com.openmc.webapp.model;

import com.openmc.webapp.service.RconService.ResourceUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RetrievalRecord Tests")
class RetrievalRecordTest {

    @Test
    @DisplayName("Should create retrieval record with all fields")
    void shouldCreateRetrievalRecordWithAllFields() {
        Instant timestamp = Instant.now();
        boolean success = true;
        int playerCount = 5;
        ResourceUsage resourceUsage = new ResourceUsage("20.0, 20.0, 20.0", "1024MB", "2048MB", "1024MB", 50.0);
        
        RetrievalRecord record = new RetrievalRecord(timestamp, success, playerCount, resourceUsage);
        
        assertEquals(timestamp, record.getTimestamp());
        assertTrue(record.isSuccess());
        assertEquals(5, record.getPlayerCount());
        assertNotNull(record.getResourceUsage());
        assertEquals("20.0, 20.0, 20.0", record.getResourceUsage().getTps());
        assertEquals("1024MB", record.getResourceUsage().getMemoryUsed());
    }

    @Test
    @DisplayName("Should handle offline status")
    void shouldHandleOfflineStatus() {
        Instant timestamp = Instant.now();
        boolean success = false;
        int playerCount = 0;
        ResourceUsage resourceUsage = new ResourceUsage("N/A", "N/A", "N/A", "N/A", 0.0);
        
        RetrievalRecord record = new RetrievalRecord(timestamp, success, playerCount, resourceUsage);
        
        assertFalse(record.isSuccess());
        assertEquals(0, record.getPlayerCount());
        assertNotNull(record.getResourceUsage());
        assertEquals("N/A", record.getResourceUsage().getTps());
    }

    @Test
    @DisplayName("Should preserve timestamp")
    void shouldPreserveTimestamp() {
        Instant before = Instant.now();
        ResourceUsage resourceUsage = new ResourceUsage("N/A", "N/A", "N/A", "N/A", 0.0);
        RetrievalRecord record = new RetrievalRecord(before, true, 0, resourceUsage);
        Instant after = Instant.now();
        
        assertEquals(before, record.getTimestamp());
        assertFalse(record.getTimestamp().isBefore(before));
        assertFalse(record.getTimestamp().isAfter(after));
    }
    
    @Test
    @DisplayName("Should include resource usage statistics")
    void shouldIncludeResourceUsageStatistics() {
        Instant timestamp = Instant.now();
        ResourceUsage resourceUsage = new ResourceUsage("19.5, 19.8, 20.0", "512MB", "1024MB", "512MB", 50.0);
        
        RetrievalRecord record = new RetrievalRecord(timestamp, true, 3, resourceUsage);
        
        assertNotNull(record.getResourceUsage());
        assertEquals("19.5, 19.8, 20.0", record.getResourceUsage().getTps());
        assertEquals("512MB", record.getResourceUsage().getMemoryUsed());
        assertEquals("1024MB", record.getResourceUsage().getMemoryMax());
        assertEquals("512MB", record.getResourceUsage().getMemoryFree());
        assertEquals(50.0, record.getResourceUsage().getMemoryUsedPercent(), 0.01);
    }
}
