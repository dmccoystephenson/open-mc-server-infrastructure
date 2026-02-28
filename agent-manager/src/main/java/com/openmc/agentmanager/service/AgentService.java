package com.openmc.agentmanager.service;

import com.openmc.agentmanager.model.AnthropicResponse;
import com.openmc.agentmanager.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service that orchestrates the agent loop:
 * user message → Anthropic API → tool call → execute → respond.
 */
@Slf4j
@Service
public class AgentService {

    private final AnthropicService anthropicService;
    private final ToolExecutionService toolExecutionService;
    private final ConfirmationService confirmationService;

    public AgentService(AnthropicService anthropicService,
                        ToolExecutionService toolExecutionService,
                        ConfirmationService confirmationService) {
        this.anthropicService = anthropicService;
        this.toolExecutionService = toolExecutionService;
        this.confirmationService = confirmationService;
    }

    /**
     * Result of processing a user message.
     */
    public record AgentResponse(String textResponse, boolean requiresConfirmation,
                                String toolName, String toolUseId,
                                java.util.List<AnthropicResponse.ContentBlock> assistantContent,
                                String userMessage) {
    }

    /**
     * Process a user message through the agent loop.
     * @param userMessage the natural language user message
     * @return the agent response, which may include a text reply or a confirmation request
     */
    public AgentResponse processMessage(String userMessage) {
        log.info("Processing user message: {}", userMessage);

        // Step 1: Send message to Anthropic API
        AnthropicResponse response = anthropicService.sendMessage(userMessage);

        if (response == null) {
            return new AgentResponse("I'm sorry, I wasn't able to process your request. Please try again.",
                    false, null, null, null, userMessage);
        }

        // Step 2: Check if the response contains a tool call
        AnthropicResponse.ContentBlock toolUseBlock = anthropicService.findToolUseBlock(response);

        if (toolUseBlock == null) {
            // No tool call — return the text response directly
            String text = anthropicService.extractTextContent(response);
            return new AgentResponse(
                    text.isEmpty() ? "I can help you start, stop, or restart the Minecraft server. What would you like to do?" : text,
                    false, null, null, null, userMessage);
        }

        // Step 3: Tool call found — check if the tool is recognized
        String toolName = toolUseBlock.getName();
        String toolUseId = toolUseBlock.getId();

        if (!toolExecutionService.isRecognizedTool(toolName)) {
            log.warn("Unrecognized tool returned by Anthropic: {}", toolName);
            return new AgentResponse(
                    "I'm sorry, I don't have the ability to perform that action. I can only start, stop, or restart the Minecraft server.",
                    false, null, null, null, userMessage);
        }

        // Step 4: Check if confirmation is required
        if (confirmationService.requiresConfirmation(toolName)) {
            log.info("Tool {} requires confirmation", toolName);
            return new AgentResponse(
                    formatConfirmationMessage(toolName),
                    true, toolName, toolUseId, response.getContent(), userMessage);
        }

        // Step 5: No confirmation needed — execute immediately
        return executeToolAndRespond(userMessage, response.getContent(), toolUseId, toolName);
    }

    /**
     * Execute a confirmed tool call and get the final response.
     * @param userMessage the original user message
     * @param assistantContent the assistant's response content
     * @param toolUseId the tool use ID
     * @param toolName the tool name
     * @return the final agent response
     */
    public AgentResponse executeToolAndRespond(String userMessage,
                                                java.util.List<AnthropicResponse.ContentBlock> assistantContent,
                                                String toolUseId, String toolName) {
        // Execute the tool
        ToolResult toolResult = toolExecutionService.executeTool(toolUseId, toolName);

        // Send the tool result back to Anthropic for a natural language response
        try {
            AnthropicResponse followUpResponse = anthropicService.sendToolResult(
                    userMessage, assistantContent, toolResult);
            String text = anthropicService.extractTextContent(followUpResponse);
            if (!text.isEmpty()) {
                return new AgentResponse(text, false, toolName, toolUseId, null, userMessage);
            }
        } catch (Exception e) {
            log.error("Failed to get follow-up response from Anthropic", e);
        }

        // Fallback to a simple response if the API follow-up fails
        String fallbackMessage = toolResult.isSuccess()
                ? "✅ " + formatToolSuccessMessage(toolName)
                : "❌ " + toolResult.getMessage();
        return new AgentResponse(fallbackMessage, false, toolName, toolUseId, null, userMessage);
    }

    private String formatConfirmationMessage(String toolName) {
        return switch (toolName) {
            case "start_server" -> "⚡ You've requested to **start** the Minecraft server. React with ✅ to confirm.";
            case "stop_server" -> "⚠️ You've requested to **stop** the Minecraft server. Players will receive shutdown warnings. React with ✅ to confirm.";
            case "restart_server" -> "🔄 You've requested to **restart** the Minecraft server. Players will receive shutdown warnings. React with ✅ to confirm.";
            default -> "React with ✅ to confirm this action.";
        };
    }

    private String formatToolSuccessMessage(String toolName) {
        return switch (toolName) {
            case "start_server" -> "Server start has been initiated.";
            case "stop_server" -> "Server stop has been initiated. Players will receive countdown warnings.";
            case "restart_server" -> "Server restart has been initiated. Players will receive countdown warnings.";
            default -> "Action completed.";
        };
    }
}
