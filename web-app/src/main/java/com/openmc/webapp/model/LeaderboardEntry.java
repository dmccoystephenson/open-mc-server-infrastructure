package com.openmc.webapp.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a player entry in the Activity Tracker leaderboard
 */
public class LeaderboardEntry {
    @Schema(description = "The player's unique identifier")
    private String playerUuid;
    @Schema(description = "The player's display name")
    private String playerName;
    @Schema(description = "Total hours played")
    private double hoursPlayed;
    @Schema(description = "Total login count")
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
}
