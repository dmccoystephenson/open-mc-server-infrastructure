package com.openmc.webapp.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a unified view of player data
 */
public class PlayerProfile {
    @Schema(description = "The player's unique identifier")
    private String playerUuid;
    @Schema(description = "The player's display name")
    private String playerName;
    @Schema(description = "Total hours the player has played")
    private double hoursPlayed;
    @Schema(description = "Total number of times the player has logged in")
    private int totalLogins;
    @Schema(description = "The player's position on the leaderboard")
    private int leaderboardRank;
    
    public PlayerProfile() {
    }
    
    public PlayerProfile(String playerUuid, String playerName, double hoursPlayed, int totalLogins, int leaderboardRank) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.hoursPlayed = hoursPlayed;
        this.totalLogins = totalLogins;
        this.leaderboardRank = leaderboardRank;
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
    
    public int getLeaderboardRank() {
        return leaderboardRank;
    }
    
    public void setLeaderboardRank(int leaderboardRank) {
        this.leaderboardRank = leaderboardRank;
    }
}
