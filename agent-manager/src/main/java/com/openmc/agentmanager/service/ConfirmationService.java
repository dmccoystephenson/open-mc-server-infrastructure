package com.openmc.agentmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for managing per-tool confirmation requirements.
 * Tracks pending confirmations and their associated tool calls.
 */
@Slf4j
@Service
public class ConfirmationService {

    private static final long CONFIRMATION_TTL_SECONDS = 300; // 5 minutes

    @Value("${agent.start-server.requires-confirmation:true}")
    private boolean startServerRequiresConfirmation;

    @Value("${agent.stop-server.requires-confirmation:true}")
    private boolean stopServerRequiresConfirmation;

    @Value("${agent.restart-server.requires-confirmation:true}")
    private boolean restartServerRequiresConfirmation;

    @Value("${agent.trigger-backup.requires-confirmation:true}")
    private boolean triggerBackupRequiresConfirmation;

    /**
     * Pending confirmation data.
     */
    public record PendingConfirmation(String toolUseId, String toolName, String userMessage,
                                      java.util.List<com.openmc.agentmanager.model.AnthropicResponse.ContentBlock> assistantContent,
                                      String channelId, String requestingUserId, String discordUsername,
                                      java.util.Map<String, Object> toolInput, Instant createdAt) {
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
            case "get_server_status" -> false;
            case "trigger_backup" -> triggerBackupRequiresConfirmation;
            case "get_server_diagnostics" -> false;
            default -> {
                log.warn("Unknown toolName '{}' passed to requiresConfirmation; defaulting to no confirmation required", toolName);
                yield false;
            }
        };
    }

    /**
     * Store a pending confirmation keyed by the Discord message ID.
     * @param messageId the Discord message ID that has the confirmation reaction
     * @param confirmation the pending confirmation data
     */
    public void addPendingConfirmation(String messageId, PendingConfirmation confirmation) {
        log.info("Added pending confirmation for message {} - tool: {} (requested by user: {})", messageId, confirmation.toolName(), confirmation.requestingUserId());
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
     * Atomically consume a pending confirmation only if the requesting user matches.
     * @param messageId the Discord message ID
     * @param userId the user ID attempting to confirm
     * @return the pending confirmation if the user matches, or null otherwise
     */
    public PendingConfirmation consumeIfRequestingUser(String messageId, String userId) {
        if (userId == null) {
            return null;
        }
        final PendingConfirmation[] result = {null};
        pendingConfirmations.computeIfPresent(messageId, (key, pending) -> {
            if (userId.equals(pending.requestingUserId())) {
                result[0] = pending;
                return null; // remove from map
            }
            return pending; // keep in map — wrong user
        });
        if (result[0] != null) {
            log.info("Consumed pending confirmation for message {} by user {} - tool: {}", messageId, userId, result[0].toolName());
        }
        return result[0];
    }

    /**
     * Check if a message has a pending confirmation.
     * @param messageId the Discord message ID
     * @return true if there is a pending confirmation
     */
    public boolean hasPendingConfirmation(String messageId) {
        return pendingConfirmations.containsKey(messageId);
    }

    /**
     * Remove expired pending confirmations.
     * Runs every 60 seconds to clean up confirmations older than the TTL.
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredConfirmations() {
        Instant cutoff = Instant.now().minusSeconds(CONFIRMATION_TTL_SECONDS);
        AtomicInteger removed = new AtomicInteger(0);
        pendingConfirmations.entrySet().removeIf(entry -> {
            if (entry.getValue().createdAt().isBefore(cutoff)) {
                log.debug("Removing expired confirmation for message {} - tool: {} (created at {})",
                        entry.getKey(), entry.getValue().toolName(), entry.getValue().createdAt());
                removed.incrementAndGet();
                return true;
            }
            return false;
        });
        if (removed.get() > 0) {
            log.info("Cleaned up {} expired pending confirmation(s)", removed.get());
        } else {
            log.debug("Confirmation cleanup ran — no expired entries (total pending: {})", pendingConfirmations.size());
        }
    }
}
