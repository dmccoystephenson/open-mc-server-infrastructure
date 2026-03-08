package com.openmc.webapp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class DeploymentRecord {
    private final Instant timestamp;
    private final String pluginName;
    private final String status;
    private final String source;
    private final String branch;
    private final String repoUrl;
    private final String message;

    @JsonCreator
    public DeploymentRecord(
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("pluginName") String pluginName,
            @JsonProperty("status") String status,
            @JsonProperty("source") String source,
            @JsonProperty("branch") String branch,
            @JsonProperty("repoUrl") String repoUrl,
            @JsonProperty("message") String message) {
        this.timestamp = timestamp;
        this.pluginName = pluginName;
        this.status = status;
        this.source = source;
        this.branch = branch;
        this.repoUrl = repoUrl;
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getPluginName() {
        return pluginName;
    }

    public String getStatus() {
        return status;
    }

    public String getSource() {
        return source;
    }

    public String getBranch() {
        return branch;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getMessage() {
        return message;
    }
}
