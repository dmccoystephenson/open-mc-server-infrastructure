package com.openmc.webapp.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LeaderboardEntry Tests")
class LeaderboardEntryTest {

    @Test
    @DisplayName("Should create empty LeaderboardEntry")
    void shouldCreateEmptyEntry() {
        LeaderboardEntry entry = new LeaderboardEntry();
        
        assertNotNull(entry);
        assertNull(entry.getPlayerUuid());
        assertNull(entry.getPlayerName());
        assertEquals(0.0, entry.getHoursPlayed());
        assertEquals(0, entry.getTotalLogins());
    }

    @Test
    @DisplayName("Should create LeaderboardEntry with values")
    void shouldCreateEntryWithValues() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        LeaderboardEntry entry = new LeaderboardEntry(uuid, "Steve", 123.45, 87);
        
        assertEquals(uuid, entry.getPlayerUuid());
        assertEquals("Steve", entry.getPlayerName());
        assertEquals(123.45, entry.getHoursPlayed());
        assertEquals(87, entry.getTotalLogins());
    }

    @Test
    @DisplayName("Should set and get player UUID")
    void shouldSetAndGetPlayerUuid() {
        LeaderboardEntry entry = new LeaderboardEntry();
        String uuid = "test-uuid-123";
        entry.setPlayerUuid(uuid);
        
        assertEquals(uuid, entry.getPlayerUuid());
    }

    @Test
    @DisplayName("Should set and get player name")
    void shouldSetAndGetPlayerName() {
        LeaderboardEntry entry = new LeaderboardEntry();
        entry.setPlayerName("Alex");
        
        assertEquals("Alex", entry.getPlayerName());
    }

    @Test
    @DisplayName("Should set and get hours played")
    void shouldSetAndGetHoursPlayed() {
        LeaderboardEntry entry = new LeaderboardEntry();
        entry.setHoursPlayed(456.78);
        
        assertEquals(456.78, entry.getHoursPlayed());
    }

    @Test
    @DisplayName("Should set and get total logins")
    void shouldSetAndGetTotalLogins() {
        LeaderboardEntry entry = new LeaderboardEntry();
        entry.setTotalLogins(42);
        
        assertEquals(42, entry.getTotalLogins());
    }
}
