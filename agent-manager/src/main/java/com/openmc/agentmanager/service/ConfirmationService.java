package com.openmc.agentmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing per-tool confirmation requirements.
 * Tracks pending confirmations and their associated tool calls.
 */
@Slf4j
@Service
public class ConfirmationService {

    @Value("${agent.start-server.requires-confirmation:true}")
    private boolean startServerRequiresConfirmation;

    @Value("${agent.stop-server.requires-confirmation:true}")
    private boolean stopServerRequiresConfirmation;

    @Value("${agent.restart-server.requires-confirmation:true}")
    private boolean restartServerRequiresConfirmation;

    /**
     * Pending confirmation data.
     */
    public record PendingConfirmation(String toolUseId, String toolName, String userMessage,
                                      java.util.List<com.openmc.agentmanager.model.AnthropicResponse.ContentBlock> assistantContent,
                                      String channelId) {
    }

    private final Map<String, PendingConfirmation> pendingConfirmations = new ConcurrentHashMap<>();

    /**
     * Check if a tool requires confirmation before execution.
     * @param toolName the name of the tool
     * @return true if the tool requires confirmation
     */
    public boolean requiresConfirmation(String toolName) {
        return switch (toolName) {
            case "start_server" -> startServerRequiresConfirmation;
            case "stop_server" -> stopServerRequiresConfirmation;
            case "restart_server" -> restartServerRequiresConfirmation;
            default -> true;
        };
    }

    /**
     * Store a pending confirmation keyed by the Discord message ID.
     * @param messageId the Discord message ID that has the confirmation reaction
     * @param confirmation the pending confirmation data
     */
    public void addPendingConfirmation(String messageId, PendingConfirmation confirmation) {
        log.info("Added pending confirmation for message {} - tool: {}", messageId, confirmation.toolName());
        pendingConfirmations.put(messageId, confirmation);
    }

    /**
     * Retrieve and remove a pending confirmation.
     * @param messageId the Discord message ID
     * @return the pending confirmation, or null if not found
     */
    public PendingConfirmation consumePendingConfirmation(String messageId) {
        PendingConfirmation confirmation = pendingConfirmations.remove(messageId);
        if (confirmation != null) {
            log.info("Consumed pending confirmation for message {} - tool: {}", messageId, confirmation.toolName());
        }
        return confirmation;
    }

    /**
     * Check if a message has a pending confirmation.
     * @param messageId the Discord message ID
     * @return true if there is a pending confirmation
     */
    public boolean hasPendingConfirmation(String messageId) {
        return pendingConfirmations.containsKey(messageId);
    }
}
