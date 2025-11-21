package com.openmc.webapp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmc.webapp.service.RconService.ResourceUsage;
import java.time.Instant;

public class RetrievalRecord {
    private final Instant timestamp;
    private final boolean success;
    private final int playerCount;
    private final ResourceUsage resourceUsage;
    
    @JsonCreator
    public RetrievalRecord(
            @JsonProperty("timestamp") Instant timestamp, 
            @JsonProperty("success") boolean success, 
            @JsonProperty("playerCount") int playerCount, 
            @JsonProperty("resourceUsage") ResourceUsage resourceUsage) {
        this.timestamp = timestamp;
        this.success = success;
        this.playerCount = playerCount;
        this.resourceUsage = resourceUsage;
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
    
    public ResourceUsage getResourceUsage() {
        return resourceUsage;
    }
}
