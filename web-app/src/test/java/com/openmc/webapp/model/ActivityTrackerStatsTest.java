package com.openmc.webapp.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ActivityTrackerStats Tests")
class ActivityTrackerStatsTest {

    @Test
    @DisplayName("Should create empty ActivityTrackerStats")
    void shouldCreateEmptyStats() {
        ActivityTrackerStats stats = new ActivityTrackerStats();
        
        assertNotNull(stats);
        assertEquals(0, stats.getUniqueLogins());
        assertEquals(0, stats.getTotalLogins());
    }

    @Test
    @DisplayName("Should create ActivityTrackerStats with values")
    void shouldCreateStatsWithValues() {
        ActivityTrackerStats stats = new ActivityTrackerStats(10, 50);
        
        assertEquals(10, stats.getUniqueLogins());
        assertEquals(50, stats.getTotalLogins());
    }

    @Test
    @DisplayName("Should set and get unique logins")
    void shouldSetAndGetUniqueLogins() {
        ActivityTrackerStats stats = new ActivityTrackerStats();
        stats.setUniqueLogins(25);
        
        assertEquals(25, stats.getUniqueLogins());
    }

    @Test
    @DisplayName("Should set and get total logins")
    void shouldSetAndGetTotalLogins() {
        ActivityTrackerStats stats = new ActivityTrackerStats();
        stats.setTotalLogins(100);
        
        assertEquals(100, stats.getTotalLogins());
    }
}
