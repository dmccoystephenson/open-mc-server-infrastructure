package com.openmc.webapp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of Activity Tracker data at a specific point in time
 */
@Entity
@Table(name = "activity_tracker_snapshots")
public class ActivityTrackerSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "unique_logins")
    private Integer uniqueLogins;

    @Column(name = "total_logins")
    private Integer totalLogins;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LeaderboardEntry> leaderboard = new ArrayList<>();

    protected ActivityTrackerSnapshot() {
    }

    @JsonCreator
    public ActivityTrackerSnapshot(
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("stats") ActivityTrackerStats stats,
            @JsonProperty("leaderboard") List<LeaderboardEntry> leaderboard,
            @JsonProperty("success") boolean success) {
        this.timestamp = timestamp;
        this.success = success;
        if (stats != null) {
            this.uniqueLogins = stats.getUniqueLogins();
            this.totalLogins = stats.getTotalLogins();
        }
        if (leaderboard != null) {
            this.leaderboard = new ArrayList<>(leaderboard);
            for (LeaderboardEntry entry : this.leaderboard) {
                entry.setSnapshot(this);
            }
        }
    }

    public Instant getTimestamp() {
        return timestamp;
    }
    
    @Transient
    public ActivityTrackerStats getStats() {
        if (uniqueLogins == null && totalLogins == null) {
            return null;
        }
        return new ActivityTrackerStats(
            uniqueLogins != null ? uniqueLogins : 0,
            totalLogins != null ? totalLogins : 0
        );
    }
    
    public List<LeaderboardEntry> getLeaderboard() {
        return leaderboard;
    }
    
    public boolean isSuccess() {
        return success;
    }
}
