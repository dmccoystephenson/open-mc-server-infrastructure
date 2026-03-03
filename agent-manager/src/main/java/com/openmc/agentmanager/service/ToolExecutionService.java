package com.openmc.agentmanager.service;

import com.openmc.agentmanager.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for executing tool calls identified by the Anthropic API.
 */
@Slf4j
@Service
public class ToolExecutionService {

    private final MinecraftWrapperService minecraftWrapperService;
    private final BackupManagerService backupManagerService;
    private final DiagnosticsService diagnosticsService;

    public ToolExecutionService(MinecraftWrapperService minecraftWrapperService,
                                BackupManagerService backupManagerService,
                                DiagnosticsService diagnosticsService) {
        this.minecraftWrapperService = minecraftWrapperService;
        this.backupManagerService = backupManagerService;
        this.diagnosticsService = diagnosticsService;
    }

    /**
     * Execute a tool call by name (without tool input).
     * @param toolUseId the tool use ID from the Anthropic response
     * @param toolName the name of the tool to execute
     * @return the result of the tool execution
     */
    public ToolResult executeTool(String toolUseId, String toolName) {
        return executeTool(toolUseId, toolName, null);
    }

    /**
     * Execute a tool call by name, optionally passing tool input parameters.
     * @param toolUseId the tool use ID from the Anthropic response
     * @param toolName the name of the tool to execute
     * @param toolInput the tool input parameters from the Anthropic response (may be null)
     * @return the result of the tool execution
     */
    public ToolResult executeTool(String toolUseId, String toolName, Map<String, Object> toolInput) {
        log.info("Executing tool: {} (ID: {})", toolName, toolUseId);
        try {
            String result = switch (toolName) {
                case "start_server" -> minecraftWrapperService.startServer();
                case "stop_server" -> minecraftWrapperService.stopServer();
                case "restart_server" -> minecraftWrapperService.restartServer();
                case "get_server_status" -> minecraftWrapperService.getServerStatus();
                case "trigger_backup" -> backupManagerService.triggerBackup();
                case "get_server_metrics" -> minecraftWrapperService.getServerMetrics();
                case "get_activity_tracker_stats" -> diagnosticsService.getActivityTrackerStats();
                case "get_activity_tracker_leaderboard" -> diagnosticsService.getActivityTrackerLeaderboard();
                case "get_server_diagnostics" -> {
                    Integer limit = null;
                    if (toolInput != null) {
                        Object rawLimit = toolInput.get("limit");
                        if (rawLimit instanceof Number number) {
                            limit = number.intValue();
                        } else if (rawLimit instanceof String str) {
                            try {
                                limit = Integer.parseInt(str.trim());
                            } catch (NumberFormatException ex) {
                                throw new RuntimeException(
                                        "Invalid 'limit' value for get_server_diagnostics: expected an integer but got '" + rawLimit + "'");
                            }
                        } else if (rawLimit != null) {
                            throw new RuntimeException(
                                    "Invalid 'limit' value for get_server_diagnostics: expected an integer but got type " + rawLimit.getClass().getSimpleName());
                        }
                    }
                    yield diagnosticsService.getServerDiagnostics(limit);
                }
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
            case "start_server", "stop_server", "restart_server", "get_server_status",
                    "trigger_backup", "get_server_metrics", "get_activity_tracker_stats",
                    "get_activity_tracker_leaderboard", "get_server_diagnostics" -> true;
            default -> false;
        };
    }
}
