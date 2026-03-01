package com.openmc.agentmanager.service;

import com.openmc.agentmanager.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for executing tool calls identified by the Anthropic API.
 */
@Slf4j
@Service
public class ToolExecutionService {

    private final MinecraftWrapperService minecraftWrapperService;

    public ToolExecutionService(MinecraftWrapperService minecraftWrapperService) {
        this.minecraftWrapperService = minecraftWrapperService;
    }

    /**
     * Execute a tool call by name.
     * @param toolUseId the tool use ID from the Anthropic response
     * @param toolName the name of the tool to execute
     * @return the result of the tool execution
     */
    public ToolResult executeTool(String toolUseId, String toolName) {
        log.info("Executing tool: {} (ID: {})", toolName, toolUseId);
        try {
            String result = switch (toolName) {
                case "start_server" -> minecraftWrapperService.startServer();
                case "stop_server" -> minecraftWrapperService.stopServer();
                case "restart_server" -> minecraftWrapperService.restartServer();
                default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
            };
            log.info("Tool {} executed successfully: {}", toolName, result);
            return ToolResult.builder()
                    .toolUseId(toolUseId)
                    .toolName(toolName)
                    .success(true)
                    .message(result)
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Unknown tool requested: {}", toolName);
            return ToolResult.builder()
                    .toolUseId(toolUseId)
                    .toolName(toolName)
                    .success(false)
                    .message("Unknown tool: " + toolName)
                    .build();
        } catch (Exception e) {
            log.error("Tool execution failed for {}: {}", toolName, e.getMessage(), e);
            return ToolResult.builder()
                    .toolUseId(toolUseId)
                    .toolName(toolName)
                    .success(false)
                    .message("Tool execution failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Check if the given tool name is a recognized tool.
     * @param toolName the tool name to check
     * @return true if the tool is recognized
     */
    public boolean isRecognizedTool(String toolName) {
        return toolName != null && switch (toolName) {
            case "start_server", "stop_server", "restart_server" -> true;
            default -> false;
        };
    }
}
