package com.openmc.minecraftwrapper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerStatus {
    private boolean running;
    private Long pid;
    private String serverJar;
    private String serverDirectory;
    /** Seconds the server process has been running; {@code null} when the server is not running. */
    private Long uptimeSeconds;
    /** UTC ISO-8601 timestamp when the server process started; {@code null} when the server is not running. */
    private String startedAt;
}
