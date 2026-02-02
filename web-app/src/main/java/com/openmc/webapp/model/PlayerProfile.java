package com.openmc.webapp.model;

/**
 * Represents a unified view of player data
 */
public class PlayerProfile {
    private String playerUuid;
    private String playerName;
    private double hoursPlayed;
    private int totalLogins;
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
