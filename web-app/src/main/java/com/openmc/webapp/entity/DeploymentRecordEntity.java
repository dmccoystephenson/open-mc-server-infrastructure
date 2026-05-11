package com.openmc.webapp.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "deployment_record")
public class DeploymentRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "plugin_name")
    private String pluginName;

    private String status;
    private String source;
    private String branch;

    @Column(name = "repo_url")
    private String repoUrl;

    private String message;

    protected DeploymentRecordEntity() {}

    public DeploymentRecordEntity(Instant timestamp, String pluginName, String status,
                                   String source, String branch, String repoUrl, String message) {
        this.timestamp = timestamp;
        this.pluginName = pluginName;
        this.status = status;
        this.source = source;
        this.branch = branch;
        this.repoUrl = repoUrl;
        this.message = message;
    }

    public Long getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public String getPluginName() { return pluginName; }
    public String getStatus() { return status; }
    public String getSource() { return source; }
    public String getBranch() { return branch; }
    public String getRepoUrl() { return repoUrl; }
    public String getMessage() { return message; }
}
