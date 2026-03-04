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
    private final AlertService alertService;

    public AgentService(AnthropicService anthropicService,
                        ToolExecutionService toolExecutionService,
                        ConfirmationService confirmationService,
                        AlertService alertService) {
        this.anthropicService = anthropicService;
        this.toolExecutionService = toolExecutionService;
        this.confirmationService = confirmationService;
        this.alertService = alertService;
    }

    /**
     * Result of processing a user message.
     */
    public record AgentResponse(String textResponse, boolean requiresConfirmation,
                                String toolName, String toolUseId,
                                java.util.List<AnthropicResponse.ContentBlock> assistantContent,
                                String userMessage,
                                java.util.Map<String, Object> toolInput) {
    }

    /**
     * Process a user message through the agent loop.
     * @param userMessage the natural language user message
     * @param discordUsername the Discord username who sent the message
     * @param permittedTools the tools the requesting user is allowed to use based on their role
     * @param roleName the display name of the requesting user's role tier
     * @return the agent response, which may include a text reply or a confirmation request
     */
    public AgentResponse processMessage(String userMessage, String discordUsername,
                                        java.util.List<com.openmc.agentmanager.model.ToolDefinition> permittedTools,
                                        String roleName) {
        log.info("Processing user message: {}", userMessage);

        // Step 0: Reject users with no permitted tools (unrecognized role)
        if (permittedTools.isEmpty()) {
            log.info("User {} has no permitted tools (role: {}). Declining request.", discordUsername, roleName);
            return new AgentResponse(
                    "I'm sorry, but you don't have permission to use any server management tools. Please contact an administrator if you believe this is a mistake.",
                    false, null, null, null, userMessage, null);
        }

        // Step 1: Send message to Anthropic API
        AnthropicResponse response = anthropicService.sendMessage(userMessage, permittedTools, roleName);

        if (response == null) {
            log.warn("Anthropic API returned null response for message: {}", userMessage);
            return new AgentResponse("I'm sorry, I wasn't able to process your request. Please try again.",
                    false, null, null, null, userMessage, null);
        }

        log.debug("Anthropic API response stop_reason: {}, content blocks: {}", response.getStopReason(),
                response.getContent() != null ? response.getContent().size() : 0);

        // Step 2: Check if the response contains a tool call
        AnthropicResponse.ContentBlock toolUseBlock = anthropicService.findToolUseBlock(response);

        if (toolUseBlock == null) {
            // No tool call — return the text response directly
            String text = anthropicService.extractTextContent(response);
            log.debug("No tool call in response. Text response length: {} chars", text.length());
            return new AgentResponse(
                    text.isEmpty() ? "I can help you manage the Minecraft server. I can start, stop, or restart the server, check its status, view performance metrics, check player activity stats and the leaderboard, trigger backups, and run diagnostics. What would you like to do?" : text,
                    false, null, null, null, userMessage, null);
        }

        // Step 3: Tool call found — check if the tool is recognized
        String toolName = toolUseBlock.getName();
        String toolUseId = toolUseBlock.getId();
        log.info("Anthropic API returned tool call: {} (ID: {})", toolName, toolUseId);

        if (!toolExecutionService.isRecognizedTool(toolName)) {
            log.warn("Unrecognized tool returned by Anthropic: {}", toolName);
            return new AgentResponse(
                    "I'm sorry, I don't have the ability to perform that action. I can start, stop, or restart the server, check its status, view performance metrics, check player activity stats and the leaderboard, trigger backups, and run diagnostics.",
                    false, null, null, null, userMessage, null);
        }

        // Step 4: Check if confirmation is required
        if (confirmationService.requiresConfirmation(toolName)) {
            log.info("Tool {} requires confirmation, prompting user", toolName);
            return new AgentResponse(
                    formatConfirmationMessage(toolName),
                    true, toolName, toolUseId, response.getContent(), userMessage, toolUseBlock.getInput());
        }

        // Step 5: No confirmation needed — execute immediately
        log.info("Tool {} does not require confirmation, executing immediately", toolName);
        return executeToolAndRespond(userMessage, response.getContent(), toolUseId, toolName, discordUsername, toolUseBlock.getInput(), permittedTools, roleName);
    }

    /**
     * Execute a confirmed tool call and get the final response.
     * @param userMessage the original user message
     * @param assistantContent the assistant's response content
     * @param toolUseId the tool use ID
     * @param toolName the tool name
     * @param discordUsername the Discord username who triggered the action
     * @param toolInput the tool input parameters from the Anthropic response (may be null)
     * @param permittedTools the tools the requesting user is allowed to use
     * @param roleName the display name of the requesting user's role tier
     * @return the final agent response
     */
    public AgentResponse executeToolAndRespond(String userMessage,
                                                java.util.List<AnthropicResponse.ContentBlock> assistantContent,
                                                String toolUseId, String toolName, String discordUsername,
                                                java.util.Map<String, Object> toolInput,
                                                java.util.List<com.openmc.agentmanager.model.ToolDefinition> permittedTools,
                                                String roleName) {
        log.info("Executing tool: {} (ID: {})", toolName, toolUseId);

        // Defense-in-depth: verify the tool is in the permitted list before executing,
        // in case the model returns an unexpected tool name or roles changed mid-flow.
        boolean isPermitted = permittedTools.stream().anyMatch(t -> t.getName().equals(toolName));
        if (!isPermitted) {
            log.warn("Tool {} is not in the permitted tools list for role {}; refusing to execute", toolName, roleName);
            return new AgentResponse(
                    "I'm sorry, but you don't have permission to use that tool.",
                    false, toolName, toolUseId, null, userMessage, null);
        }

        // Execute the tool
        ToolResult toolResult = toolExecutionService.executeTool(toolUseId, toolName, toolInput);
        log.info("Tool {} execution result: success={}, message={}", toolName, toolResult.isSuccess(), toolResult.getMessage());

        // Send alert for tool execution
        alertService.sendToolExecutionAlert(discordUsername, toolName, userMessage, toolResult.isSuccess(), roleName);

        // Send the tool result back to Anthropic for a natural language response
        try {
            log.debug("Sending tool result back to Anthropic for natural language summary");
            AnthropicResponse followUpResponse = anthropicService.sendToolResult(
                    userMessage, assistantContent, toolResult, permittedTools, roleName);
            String text = anthropicService.extractTextContent(followUpResponse);
            if (!text.isEmpty()) {
                log.debug("Received follow-up response from Anthropic ({} chars)", text.length());
                return new AgentResponse(text, false, toolName, toolUseId, null, userMessage, null);
            }
            log.warn("Anthropic follow-up response was empty, falling back to default message");
        } catch (Exception e) {
            log.error("Failed to get follow-up response from Anthropic", e);
        }

        // Fallback to a simple response if the API follow-up fails
        String fallbackMessage = toolResult.isSuccess()
                ? "✅ " + formatToolSuccessMessage(toolName)
                : "❌ " + toolResult.getMessage();
        return new AgentResponse(fallbackMessage, false, toolName, toolUseId, null, userMessage, null);
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
