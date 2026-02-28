package com.openmc.agentmanager.model;

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
     * Returns all available tool definitions.
     */
    public static List<ToolDefinition> allTools() {
        return List.of(startServer(), stopServer(), restartServer());
    }
}
