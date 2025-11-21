package com.openmc.webapp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * Snapshot of Activity Tracker data at a specific point in time
 */
public class ActivityTrackerSnapshot {
    private final Instant timestamp;
    private final ActivityTrackerStats stats;
    private final List<LeaderboardEntry> leaderboard;
    private final boolean success;
    
    @JsonCreator
    public ActivityTrackerSnapshot(
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("stats") ActivityTrackerStats stats,
            @JsonProperty("leaderboard") List<LeaderboardEntry> leaderboard,
            @JsonProperty("success") boolean success) {
        this.timestamp = timestamp;
        this.stats = stats;
        this.leaderboard = leaderboard;
        this.success = success;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public ActivityTrackerStats getStats() {
        return stats;
    }
    
    public List<LeaderboardEntry> getLeaderboard() {
        return leaderboard;
    }
    
    public boolean isSuccess() {
        return success;
    }
}
