package com.openmc.webapp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openmc.webapp.service.RconService.ResourceUsage;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "retrieval_records")
public class RetrievalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "player_count", nullable = false)
    private int playerCount;

    @Column
    private String tps;

    @Column(name = "memory_used")
    private String memoryUsed;

    @Column(name = "memory_max")
    private String memoryMax;

    @Column(name = "memory_free")
    private String memoryFree;

    @Column(name = "memory_used_percent")
    private double memoryUsedPercent;

    protected RetrievalRecord() {
    }

    @JsonCreator
    public RetrievalRecord(
            @JsonProperty("timestamp") Instant timestamp, 
            @JsonProperty("success") boolean success, 
            @JsonProperty("playerCount") int playerCount, 
            @JsonProperty("resourceUsage") ResourceUsage resourceUsage) {
        this.timestamp = timestamp;
        this.success = success;
        this.playerCount = playerCount;
        if (resourceUsage != null) {
            this.tps = resourceUsage.getTps();
            this.memoryUsed = resourceUsage.getMemoryUsed();
            this.memoryMax = resourceUsage.getMemoryMax();
            this.memoryFree = resourceUsage.getMemoryFree();
            this.memoryUsedPercent = resourceUsage.getMemoryUsedPercent();
        }
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
    
    @Transient
    public ResourceUsage getResourceUsage() {
        return new ResourceUsage(tps, memoryUsed, memoryMax, memoryFree, memoryUsedPercent);
    }
}
