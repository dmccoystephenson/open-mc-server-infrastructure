package com.openmc.webapp.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "activity_tracker_snapshot")
public class ActivityTrackerSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "unique_logins")
    private Integer uniqueLogins;

    @Column(name = "total_logins")
    private Integer totalLogins;

    @Column(columnDefinition = "TEXT")
    private String leaderboard;

    protected ActivityTrackerSnapshotEntity() {}

    public ActivityTrackerSnapshotEntity(Instant timestamp, boolean success,
                                         Integer uniqueLogins, Integer totalLogins,
                                         String leaderboard) {
        this.timestamp = timestamp;
        this.success = success;
        this.uniqueLogins = uniqueLogins;
        this.totalLogins = totalLogins;
        this.leaderboard = leaderboard;
    }

    public Long getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public boolean isSuccess() { return success; }
    public Integer getUniqueLogins() { return uniqueLogins; }
    public Integer getTotalLogins() { return totalLogins; }
    public String getLeaderboard() { return leaderboard; }
}
