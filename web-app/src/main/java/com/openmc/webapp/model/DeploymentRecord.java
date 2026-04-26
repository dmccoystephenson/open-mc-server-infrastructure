package com.openmc.webapp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public class DeploymentRecord {
    @Schema(description = "When the deployment occurred")
    private final Instant timestamp;
    @Schema(description = "Name of the deployed plugin")
    private final String pluginName;
    @Schema(description = "Deployment status (SUCCESS or FAILURE)")
    private final String status;
    @Schema(description = "Source of the deployment")
    private final String source;
    @Schema(description = "Git branch name")
    private final String branch;
    @Schema(description = "Repository URL")
    private final String repoUrl;
    @Schema(description = "Additional deployment message")
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
