package com.openmc.webapp.model;

import java.time.Instant;

public class RetrievalRecord {
    private final Instant timestamp;
    private final boolean success;
    private final int playerCount;
    private final String playerList;
    
    public RetrievalRecord(Instant timestamp, boolean success, int playerCount, String playerList) {
        this.timestamp = timestamp;
        this.success = success;
        this.playerCount = playerCount;
        this.playerList = playerList;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public int getPlayerCount() {
        return playerCount;
    }
    
    public String getPlayerList() {
        return playerList;
    }
}
