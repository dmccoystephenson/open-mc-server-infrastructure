package com.openmc.webapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/**
 * Represents a player entry in the Activity Tracker leaderboard
 */
@Entity
@Table(name = "leaderboard_entries")
public class LeaderboardEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    @JsonIgnore
    private ActivityTrackerSnapshot snapshot;

    @Column(name = "player_uuid")
    private String playerUuid;

    @Column(name = "player_name")
    private String playerName;

    @Column(name = "hours_played")
    private double hoursPlayed;

    @Column(name = "total_logins")
    private int totalLogins;
    
    public LeaderboardEntry() {
    }
    
    public LeaderboardEntry(String playerUuid, String playerName, double hoursPlayed, int totalLogins) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.hoursPlayed = hoursPlayed;
        this.totalLogins = totalLogins;
    }
    
    public String getPlayerUuid() {
        return playerUuid;
    }
    
    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
    
    public double getHoursPlayed() {
        return hoursPlayed;
    }
    
    public void setHoursPlayed(double hoursPlayed) {
        this.hoursPlayed = hoursPlayed;
    }
    
    public int getTotalLogins() {
        return totalLogins;
    }
    
    public void setTotalLogins(int totalLogins) {
        this.totalLogins = totalLogins;
    }

    @JsonIgnore
    public ActivityTrackerSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(ActivityTrackerSnapshot snapshot) {
        this.snapshot = snapshot;
    }
}
