package com.openmc.alertmanager.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "alert_record")
public class AlertRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String message;
    private String level;
    private String source;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected AlertRecordEntity() {}

    public AlertRecordEntity(String title, String message, String level, String source, Instant receivedAt) {
        this.title = title;
        this.message = message;
        this.level = level;
        this.source = source;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getLevel() { return level; }
    public String getSource() { return source; }
    public Instant getReceivedAt() { return receivedAt; }
}
