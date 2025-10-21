package com.openmc.webapp.model;

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
        String playerList = "There are 5 of a max of 20 players online";
        
        RetrievalRecord record = new RetrievalRecord(timestamp, success, playerCount, playerList);
        
        assertEquals(timestamp, record.getTimestamp());
        assertTrue(record.isSuccess());
        assertEquals(5, record.getPlayerCount());
        assertEquals(playerList, record.getPlayerList());
    }

    @Test
    @DisplayName("Should handle offline status")
    void shouldHandleOfflineStatus() {
        Instant timestamp = Instant.now();
        boolean success = false;
        int playerCount = 0;
        String playerList = "Error: Unable to connect to server";
        
        RetrievalRecord record = new RetrievalRecord(timestamp, success, playerCount, playerList);
        
        assertFalse(record.isSuccess());
        assertEquals(0, record.getPlayerCount());
        assertTrue(record.getPlayerList().startsWith("Error:"));
    }

    @Test
    @DisplayName("Should preserve timestamp")
    void shouldPreserveTimestamp() {
        Instant before = Instant.now();
        RetrievalRecord record = new RetrievalRecord(before, true, 0, "test");
        Instant after = Instant.now();
        
        assertEquals(before, record.getTimestamp());
        assertFalse(record.getTimestamp().isBefore(before));
        assertFalse(record.getTimestamp().isAfter(after));
    }
}
