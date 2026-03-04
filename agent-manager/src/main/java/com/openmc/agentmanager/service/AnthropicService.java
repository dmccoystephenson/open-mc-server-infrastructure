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

    // Usage guidance appended to each tool's base description in the system prompt.
    // The base description comes from ToolDefinition.description so there is only one
    // source of truth for the tool name and core description.
    private static final String USAGE_GUIDANCE_START_SERVER =
            "Usage guidance: Call this only when the Minecraft server is currently stopped and the user explicitly wants it brought online.";
    private static final String USAGE_GUIDANCE_STOP_SERVER =
            "Usage guidance: Use this to gracefully stop the server when the user requests a shutdown. Avoid stopping the server unexpectedly unless there is a clear operational reason.";
    private static final String USAGE_GUIDANCE_RESTART_SERVER =
            "Usage guidance: Prefer this when the user wants a restart or when a configuration change requires a full restart. It should perform an orderly stop (with warnings) before starting again.";
    private static final String USAGE_GUIDANCE_GET_SERVER_STATUS =
            "Usage guidance: Use this when the user asks whether the server is up, down, or for high-level runtime information such as PID or uptime.";
    private static final String USAGE_GUIDANCE_TRIGGER_BACKUP =
            "Usage guidance: Use this for on-demand backups when the user requests a backup or before performing risky operations that might affect world data.";
    private static final String USAGE_GUIDANCE_GET_SERVER_METRICS =
            "Usage guidance: Use this when the user asks about lag, TPS, memory usage, or performance characteristics. Prefer this over broad diagnostics when the question is specifically about performance metrics.";
    private static final String USAGE_GUIDANCE_GET_ACTIVITY_TRACKER_STATS =
            "Usage guidance: Use this when the user asks about overall player activity levels, such as total logins or unique players over time.";
    private static final String USAGE_GUIDANCE_GET_ACTIVITY_TRACKER_LEADERBOARD =
            "Usage guidance: Use this when the user wants a ranking or leaderboard of players by time played or login counts, or asks who has played the most.";
    private static final String USAGE_GUIDANCE_GET_SERVER_DIAGNOSTICS =
            "Usage guidance: Use this for open-ended health questions such as \"is the server okay?\", \"why is it lagging?\", or \"what happened while I was offline?\". Prefer the more focused tools (metrics, activity stats, leaderboard) when the user's question clearly maps to a single data source. After receiving the JSON result, summarize only the information relevant to the user's question; do not dump all fields. If any source was unavailable, mention that only when it affects the answer.";

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
                .map(t -> "- " + toolDescription(t))
                .collect(Collectors.joining("\n"));
        return String.format("""
                You are a Minecraft server management assistant helping a %s. Your sole purpose is to help users manage their Minecraft server. The following tools are available to them:
                
                %s
                
                You should only use these tools when the user clearly requests a server management action. For any requests outside of server management, politely explain that you can only help with managing the Minecraft server.
                
                Be concise and helpful in your responses.""", roleName, toolList);
    }

    private static String toolDescription(ToolDefinition tool) {
        String base = tool.getName() + ": " + tool.getDescription();
        String guidance = usageGuidance(tool.getName());
        return guidance.isEmpty() ? base : base + " " + guidance;
    }

    private static String usageGuidance(String toolName) {
        return switch (toolName) {
            case "start_server" -> USAGE_GUIDANCE_START_SERVER;
            case "stop_server" -> USAGE_GUIDANCE_STOP_SERVER;
            case "restart_server" -> USAGE_GUIDANCE_RESTART_SERVER;
            case "get_server_status" -> USAGE_GUIDANCE_GET_SERVER_STATUS;
            case "trigger_backup" -> USAGE_GUIDANCE_TRIGGER_BACKUP;
            case "get_server_metrics" -> USAGE_GUIDANCE_GET_SERVER_METRICS;
            case "get_activity_tracker_stats" -> USAGE_GUIDANCE_GET_ACTIVITY_TRACKER_STATS;
            case "get_activity_tracker_leaderboard" -> USAGE_GUIDANCE_GET_ACTIVITY_TRACKER_LEADERBOARD;
            case "get_server_diagnostics" -> USAGE_GUIDANCE_GET_SERVER_DIAGNOSTICS;
            default -> "";
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
