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
     * Creates the get_server_diagnostics tool definition.
     */
    public static ToolDefinition getServerDiagnostics() {
        return ToolDefinition.builder()
                .name("get_server_diagnostics")
                .description("Gathers diagnostic context from multiple sources (server status, recent alerts, " +
                        "latest backup, and optionally recent server logs) and returns a structured JSON summary. " +
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
     * Returns all available tool definitions.
     */
    public static List<ToolDefinition> allTools() {
        return List.of(startServer(), stopServer(), restartServer(), getServerStatus(), triggerBackup(), getServerDiagnostics());
    }
}
