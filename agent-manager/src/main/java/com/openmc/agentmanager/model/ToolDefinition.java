package com.openmc.agentmanager.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a tool definition for the Anthropic API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    private String name;
    private String description;

    @JsonProperty("input_schema")
    private Map<String, Object> inputSchema;

    /**
     * Creates the start_server tool definition.
     */
    public static ToolDefinition startServer() {
        return ToolDefinition.builder()
                .name("start_server")
                .description("Starts the Minecraft server instance managed by minecraft-wrapper.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()
                ))
                .build();
    }

    /**
     * Creates the stop_server tool definition.
     */
    public static ToolDefinition stopServer() {
        return ToolDefinition.builder()
                .name("stop_server")
                .description("Gracefully stops the Minecraft server, warning online players before shutdown.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()
                ))
                .build();
    }

    /**
     * Creates the restart_server tool definition.
     */
    public static ToolDefinition restartServer() {
        return ToolDefinition.builder()
                .name("restart_server")
                .description("Gracefully stops and then starts the Minecraft server.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()
                ))
                .build();
    }

    /**
     * Creates the get_server_status tool definition.
     */
    public static ToolDefinition getServerStatus() {
        return ToolDefinition.builder()
                .name("get_server_status")
                .description("Gets the current status of the Minecraft server, including whether it is running.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()
                ))
                .build();
    }

    /**
     * Creates the trigger_backup tool definition.
     */
    public static ToolDefinition triggerBackup() {
        return ToolDefinition.builder()
                .name("trigger_backup")
                .description("Triggers a manual backup of the Minecraft server world data.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()
                ))
                .build();
    }

    /**
     * Creates the get_server_metrics tool definition.
     */
    public static ToolDefinition getServerMetrics() {
        return ToolDefinition.builder()
                .name("get_server_metrics")
                .description("Gets live server performance metrics from the Minecraft wrapper: JVM heap usage " +
                        "(used/max MB and percentage), TPS from the last 1m/5m/15m (Paper/Spigot only), " +
                        "server process RSS memory, and server uptime in seconds. " +
                        "Use this when the user asks specifically about lag, TPS, or memory usage.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()
                ))
                .build();
    }

    /**
     * Creates the get_activity_tracker_stats tool definition.
     */
    public static ToolDefinition getActivityTrackerStats() {
        return ToolDefinition.builder()
                .name("get_activity_tracker_stats")
                .description("Fetches aggregate player activity statistics from the webapp: total and unique " +
                        "login counts. Use this when the user asks about overall player activity figures.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()
                ))
                .build();
    }

    /**
     * Creates the get_activity_tracker_leaderboard tool definition.
     */
    public static ToolDefinition getActivityTrackerLeaderboard() {
        return ToolDefinition.builder()
                .name("get_activity_tracker_leaderboard")
                .description("Fetches the ranked player leaderboard from the webapp's Activity Tracker. " +
                        "Returns a list of players sorted by play time, each with player name, hours played, " +
                        "and total login count. Use this when the user asks who has played the most, " +
                        "requests a leaderboard, or wants to know top players by activity.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()
                ))
                .build();
    }

    /**
     * Creates the get_server_diagnostics tool definition.
     */
    public static ToolDefinition getServerDiagnostics() {
        return ToolDefinition.builder()
                .name("get_server_diagnostics")
                .description("Gathers diagnostic context from multiple sources (server status, recent alerts, " +
                        "latest backup, server performance metrics including JVM heap usage and TPS, " +
                        "and optionally recent server logs) and returns a structured JSON summary. " +
                        "Use this instead of get_server_status when the user asks an open-ended health question such as " +
                        "'is the server okay?', 'why is it lagging?', or 'what happened while I was offline?'. " +
                        "Always read-only — never requires confirmation.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "limit", Map.of(
                                        "type", "integer",
                                        "description", "Maximum number of recent alerts to include (default: 10, max: 100)."
                                )
                        ),
                        "required", List.of()
                ))
                .build();
    }

    /**
     * Creates the get_repository_info tool definition.
     */
    public static ToolDefinition getRepositoryInfo() {
        return ToolDefinition.builder()
                .name("get_repository_info")
                .description("Retrieves information about the OMCSI (Open MC Server Infrastructure) repository, " +
                        "its services, architecture, configuration, and usage. Use this when the user asks about " +
                        "the infrastructure itself, how to set it up, what services are available, how they are " +
                        "configured, or any other question about the OMCSI project. " +
                        "Always read-only — never requires confirmation.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "topic", Map.of(
                                        "type", "string",
                                        "description", "The specific topic to query. Available topics: " +
                                                "overview, services, getting_started, architecture, scripts, " +
                                                "configuration, self_hosting, ci_cd. " +
                                                "If omitted, returns all repository information."
                                )
                        ),
                        "required", List.of()
                ))
                .build();
    }

    /**
     * Returns all available tool definitions.
     */
    public static List<ToolDefinition> allTools() {
        return List.of(startServer(), stopServer(), restartServer(), getServerStatus(), triggerBackup(),
                getServerMetrics(), getActivityTrackerStats(), getActivityTrackerLeaderboard(), getServerDiagnostics(),
                getRepositoryInfo());
    }
}
