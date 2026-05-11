package com.openmc.webapp.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "retrieval_record")
public class RetrievalRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "player_count", nullable = false)
    private int playerCount;

    private String tps;

    @Column(name = "memory_used")
    private String memoryUsed;

    @Column(name = "memory_max")
    private String memoryMax;

    @Column(name = "memory_free")
    private String memoryFree;

    @Column(name = "memory_used_percent")
    private Double memoryUsedPercent;

    protected RetrievalRecordEntity() {}

    public RetrievalRecordEntity(Instant timestamp, boolean success, int playerCount,
                                  String tps, String memoryUsed, String memoryMax,
                                  String memoryFree, Double memoryUsedPercent) {
        this.timestamp = timestamp;
        this.success = success;
        this.playerCount = playerCount;
        this.tps = tps;
        this.memoryUsed = memoryUsed;
        this.memoryMax = memoryMax;
        this.memoryFree = memoryFree;
        this.memoryUsedPercent = memoryUsedPercent;
    }

    public Long getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public boolean isSuccess() { return success; }
    public int getPlayerCount() { return playerCount; }
    public String getTps() { return tps; }
    public String getMemoryUsed() { return memoryUsed; }
    public String getMemoryMax() { return memoryMax; }
    public String getMemoryFree() { return memoryFree; }
    public Double getMemoryUsedPercent() { return memoryUsedPercent; }
}
