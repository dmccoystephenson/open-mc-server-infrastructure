package com.openmc.agentmanager.service;

import com.openmc.agentmanager.model.AnthropicRequest;
import com.openmc.agentmanager.model.AnthropicResponse;
import com.openmc.agentmanager.model.ToolDefinition;
import com.openmc.agentmanager.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for communicating with the Anthropic Messages API.
 */
@Slf4j
@Service
public class AnthropicService {

    private static final String TOOL_DESCRIPTION_START_SERVER =
            "start_server: Starts the Minecraft server";
    private static final String TOOL_DESCRIPTION_STOP_SERVER =
            "stop_server: Gracefully stops the Minecraft server (players receive countdown warnings)";
    private static final String TOOL_DESCRIPTION_RESTART_SERVER =
            "restart_server: Gracefully restarts the Minecraft server (stop with warnings, then start)";
    private static final String TOOL_DESCRIPTION_GET_SERVER_STATUS =
            "get_server_status: Gets the current status of the Minecraft server (running state, PID, uptime, etc.)";
    private static final String TOOL_DESCRIPTION_TRIGGER_BACKUP =
            "trigger_backup: Triggers a manual backup of the Minecraft server world data";
    private static final String TOOL_DESCRIPTION_GET_SERVER_METRICS =
            "get_server_metrics: Gets live performance metrics from the Minecraft wrapper: JVM heap usage (used/max MB and percentage), TPS for the last 1m/5m/15m (Paper/Spigot only), server process memory, and server uptime. Use this when the user asks specifically about lag, TPS, or memory usage.";
    private static final String TOOL_DESCRIPTION_GET_ACTIVITY_TRACKER_STATS =
            "get_activity_tracker_stats: Fetches aggregate player activity statistics from the webapp: total and unique login counts. Use this when the user asks about overall player activity figures.";
    private static final String TOOL_DESCRIPTION_GET_ACTIVITY_TRACKER_LEADERBOARD =
            "get_activity_tracker_leaderboard: Fetches the ranked player leaderboard from the webapp's Activity Tracker — player name, hours played, and total login count per player, sorted by play time. Use this when the user asks who has played the most, requests a leaderboard, or wants to know the top players by activity.";
    private static final String TOOL_DESCRIPTION_GET_SERVER_DIAGNOSTICS =
            "get_server_diagnostics: Gathers context from multiple sources in a single pass — server status, recent alerts, latest backup result, server performance metrics, and (when enabled) recent server logs and webapp activity stats. Use this for open-ended health questions such as \"is the server okay?\", \"why is it lagging?\", or \"what happened while I was offline?\". Prefer the more focused tools above (get_server_metrics, get_activity_tracker_stats, get_activity_tracker_leaderboard) when the user's question maps clearly to a single data source. IMPORTANT: After receiving the JSON result, reply with ONLY the information relevant to what the user asked — do not dump all fields. If any source was unavailable, acknowledge the gap only if it is relevant to the user's question.";

    private final RestTemplate restTemplate;

    @Value("${anthropic.api.key:}")
    private String apiKey;

    @Value("${anthropic.api.url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    @Value("${anthropic.model:claude-sonnet-4-20250514}")
    private String model;

    public AnthropicService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Build a role-specific system prompt that lists only the permitted tools.
     * @param roleName the display name of the requesting user's role tier
     * @param permittedTools the tools available to this user
     * @return the system prompt string
     */
    static String buildSystemPrompt(String roleName, List<ToolDefinition> permittedTools) {
        if (permittedTools.isEmpty()) {
            return "You are a Minecraft server management assistant. The requesting user does not have permission to use any tools. Politely inform them that they do not have access to server management actions.";
        }
        String toolList = permittedTools.stream()
                .map(t -> "- " + toolDescription(t.getName()))
                .collect(Collectors.joining("\n"));
        return String.format("""
                You are a Minecraft server management assistant helping a %s. Your sole purpose is to help users manage their Minecraft server. The following tools are available to them:
                
                %s
                
                You should only use these tools when the user clearly requests a server management action. For any requests outside of server management, politely explain that you can only help with managing the Minecraft server.
                
                Be concise and helpful in your responses.""", roleName, toolList);
    }

    private static String toolDescription(String toolName) {
        return switch (toolName) {
            case "start_server" -> TOOL_DESCRIPTION_START_SERVER;
            case "stop_server" -> TOOL_DESCRIPTION_STOP_SERVER;
            case "restart_server" -> TOOL_DESCRIPTION_RESTART_SERVER;
            case "get_server_status" -> TOOL_DESCRIPTION_GET_SERVER_STATUS;
            case "trigger_backup" -> TOOL_DESCRIPTION_TRIGGER_BACKUP;
            case "get_server_metrics" -> TOOL_DESCRIPTION_GET_SERVER_METRICS;
            case "get_activity_tracker_stats" -> TOOL_DESCRIPTION_GET_ACTIVITY_TRACKER_STATS;
            case "get_activity_tracker_leaderboard" -> TOOL_DESCRIPTION_GET_ACTIVITY_TRACKER_LEADERBOARD;
            case "get_server_diagnostics" -> TOOL_DESCRIPTION_GET_SERVER_DIAGNOSTICS;
            default -> toolName;
        };
    }

    /**
     * Send a user message to the Anthropic API and get a response.
     * @param userMessage the user's natural language message
     * @param permittedTools the tools the requesting user is allowed to use
     * @param roleName the display name of the requesting user's role tier
     * @return the Anthropic API response
     */
    public AnthropicResponse sendMessage(String userMessage, List<ToolDefinition> permittedTools, String roleName) {
        log.info("Sending message to Anthropic API");
        log.debug("User message: {}", userMessage);
        List<AnthropicRequest.Message> messages = new ArrayList<>();
        messages.add(AnthropicRequest.Message.builder()
                .role("user")
                .content(userMessage)
                .build());

        return callApi(messages, permittedTools, buildSystemPrompt(roleName, permittedTools));
    }

    /**
     * Send a tool result back to the Anthropic API to get a natural language response.
     * @param userMessage the original user message
     * @param assistantContent the assistant's response content (containing the tool_use block)
     * @param toolResult the result from executing the tool
     * @param permittedTools the tools the requesting user is allowed to use
     * @param roleName the display name of the requesting user's role tier
     * @return the Anthropic API response with a natural language summary
     */
    public AnthropicResponse sendToolResult(String userMessage, List<AnthropicResponse.ContentBlock> assistantContent,
                                            ToolResult toolResult, List<ToolDefinition> permittedTools, String roleName) {
        log.info("Sending tool result to Anthropic API for tool: {} (success={})", toolResult.getToolName(), toolResult.isSuccess());
        log.debug("Tool result message: {}", toolResult.getMessage());
        List<AnthropicRequest.Message> messages = new ArrayList<>();

        messages.add(AnthropicRequest.Message.builder()
                .role("user")
                .content(userMessage)
                .build());

        // Convert assistant content blocks to serializable format
        List<Map<String, Object>> assistantContentMaps = new ArrayList<>();
        for (AnthropicResponse.ContentBlock block : assistantContent) {
            if ("text".equals(block.getType())) {
                assistantContentMaps.add(Map.of("type", "text", "text", block.getText()));
            } else if ("tool_use".equals(block.getType())) {
                assistantContentMaps.add(Map.of(
                        "type", "tool_use",
                        "id", block.getId(),
                        "name", block.getName(),
                        "input", block.getInput() != null ? block.getInput() : Map.of()
                ));
            }
        }

        messages.add(AnthropicRequest.Message.builder()
                .role("assistant")
                .content(assistantContentMaps)
                .build());

        // Add tool result message
        List<Map<String, Object>> toolResultContent = List.of(Map.of(
                "type", "tool_result",
                "tool_use_id", toolResult.getToolUseId(),
                "content", toolResult.getMessage()
        ));

        messages.add(AnthropicRequest.Message.builder()
                .role("user")
                .content(toolResultContent)
                .build());

        return callApi(messages, permittedTools, buildSystemPrompt(roleName, permittedTools));
    }

    private AnthropicResponse callApi(List<AnthropicRequest.Message> messages, List<ToolDefinition> tools, String systemPrompt) {
        log.debug("Building Anthropic API request with model: {}, max_tokens: 1024, tools: {}", model, tools.size());
        AnthropicRequest request = AnthropicRequest.builder()
                .model(model)
                .system(systemPrompt)
                .maxTokens(1024)
                .messages(messages)
                .tools(tools)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        HttpEntity<AnthropicRequest> entity = new HttpEntity<>(request, headers);

        try {
            log.debug("Calling Anthropic API at {}", apiUrl);
            ResponseEntity<AnthropicResponse> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity, AnthropicResponse.class);
            AnthropicResponse body = response.getBody();
            log.debug("Anthropic API response status: {}, stop_reason: {}", response.getStatusCode(),
                    body != null ? body.getStopReason() : "null");
            return body;
        } catch (Exception e) {
            log.error("Failed to call Anthropic API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Anthropic API: " + e.getMessage(), e);
        }
    }

    /**
     * Extract text content from an Anthropic response.
     * @param response the Anthropic API response
     * @return the text content, or empty string if none
     */
    public String extractTextContent(AnthropicResponse response) {
        if (response == null || response.getContent() == null) {
            return "";
        }
        return response.getContent().stream()
                .filter(block -> "text".equals(block.getType()))
                .map(AnthropicResponse.ContentBlock::getText)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Find the first tool_use block in an Anthropic response.
     * @param response the Anthropic API response
     * @return the tool_use content block, or null if none
     */
    public AnthropicResponse.ContentBlock findToolUseBlock(AnthropicResponse response) {
        if (response == null || response.getContent() == null) {
            return null;
        }
        return response.getContent().stream()
                .filter(block -> "tool_use".equals(block.getType()))
                .findFirst()
                .orElse(null);
    }
}
