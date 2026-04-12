package com.openmc.minecraftwrapper.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerStatus {
    @Schema(description = "Whether the Minecraft server process is currently running")
    private boolean running;

    @Schema(description = "Process ID of the Minecraft server")
    private Long pid;

    @Schema(description = "Name of the server JAR file")
    private String serverJar;

    @Schema(description = "Path to the server directory")
    private String serverDirectory;

    /** Seconds the server process has been running; {@code null} when the server is not running. */
    @Schema(description = "Seconds the server process has been running")
    private Long uptimeSeconds;

    /** UTC ISO-8601 timestamp when the server process started; {@code null} when the server is not running. */
    @Schema(description = "UTC ISO-8601 timestamp when the server started")
    private String startedAt;
}
