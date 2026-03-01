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

    private static final String SYSTEM_PROMPT = """
            You are a Minecraft server management assistant. Your sole purpose is to help users manage \
            their Minecraft server. You have access to the following tools:
            
            - start_server: Starts the Minecraft server
            - stop_server: Gracefully stops the Minecraft server (players receive countdown warnings)
            - restart_server: Gracefully restarts the Minecraft server (stop with warnings, then start)
            - get_server_status: Gets the current status of the Minecraft server (running state, etc.)
            - trigger_backup: Triggers a manual backup of the Minecraft server world data
            - get_server_diagnostics: Gathers context from multiple sources (server status, recent alerts, \
            latest backup) and returns a structured JSON blob. Use this tool — instead of get_server_status — \
            when the user asks an open-ended health question such as "is the server okay?", \
            "why is it lagging?", or "what happened while I was offline?". The tool is always read-only. \
            After receiving the JSON result, synthesize a concise natural language summary that connects \
            the data points (e.g. correlate a crash time with the last backup window). If any source was \
            unavailable, acknowledge the gap in your response.
            
            You should only use these tools when the user clearly requests a server management action. \
            For any requests outside of server management, politely explain that you can only help with \
            managing the Minecraft server.
            
            Be concise and helpful in your responses.""";

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
     * Send a user message to the Anthropic API and get a response.
     * @param userMessage the user's natural language message
     * @return the Anthropic API response
     */
    public AnthropicResponse sendMessage(String userMessage) {
        log.info("Sending message to Anthropic API");
        log.debug("User message: {}", userMessage);
        List<AnthropicRequest.Message> messages = new ArrayList<>();
        messages.add(AnthropicRequest.Message.builder()
                .role("user")
                .content(userMessage)
                .build());

        return callApi(messages);
    }

    /**
     * Send a tool result back to the Anthropic API to get a natural language response.
     * @param userMessage the original user message
     * @param assistantContent the assistant's response content (containing the tool_use block)
     * @param toolResult the result from executing the tool
     * @return the Anthropic API response with a natural language summary
     */
    public AnthropicResponse sendToolResult(String userMessage, List<AnthropicResponse.ContentBlock> assistantContent, ToolResult toolResult) {
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

        return callApi(messages);
    }

    private AnthropicResponse callApi(List<AnthropicRequest.Message> messages) {
        log.debug("Building Anthropic API request with model: {}, max_tokens: 1024, tools: {}", model, ToolDefinition.allTools().size());
        AnthropicRequest request = AnthropicRequest.builder()
                .model(model)
                .system(SYSTEM_PROMPT)
                .maxTokens(1024)
                .messages(messages)
                .tools(ToolDefinition.allTools())
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
