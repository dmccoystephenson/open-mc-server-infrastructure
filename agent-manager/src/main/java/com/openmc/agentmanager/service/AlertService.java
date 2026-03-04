package com.openmc.agentmanager.service;

import com.openmc.agentmanager.model.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Service for sending alerts to the alert-manager when tool executions occur.
 */
@Slf4j
@Service
public class AlertService {

    private final RestTemplate restTemplate;

    @Value("${alert.manager.url:}")
    private String alertManagerUrl;

    public AlertService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Send an alert to alert-manager when a tool execution occurs.
     * @param discordUsername the Discord username who triggered the action
     * @param toolName the name of the tool that was executed
     * @param originalPrompt the original natural language prompt from the user
     * @param success whether the tool execution was successful
     * @param roleName the display name of the requesting user's role tier
     */
    public void sendToolExecutionAlert(String discordUsername, String toolName, String originalPrompt, boolean success, String roleName) {
        if (alertManagerUrl == null || alertManagerUrl.isBlank()) {
            log.debug("Alert manager URL is not configured. Skipping tool execution alert.");
            return;
        }

        String actionDescription = formatActionDescription(toolName);
        String level = success ? "INFO" : "WARNING";
        String title = success
                ? "Agent Action Executed: " + actionDescription
                : "Agent Action Failed: " + actionDescription;
        String message = String.format(
                "**Discord User**: %s\n**Role**: %s\n**Action**: %s\n**Result**: %s\n**Original Prompt**: %s",
                discordUsername,
                roleName,
                actionDescription,
                success ? "Success" : "Failed",
                originalPrompt
        );

        Alert alert = Alert.builder()
                .title(title)
                .message(message)
                .level(level)
                .source("agent-manager")
                .destinations(List.of("DISCORD"))
                .build();

        try {
            log.info("Sending tool execution alert to {}: {} by {}", alertManagerUrl, toolName, discordUsername);
            log.debug("Alert payload: title='{}', level='{}', message length={}", title, level, message.length());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Alert> request = new HttpEntity<>(alert, headers);

            restTemplate.postForEntity(alertManagerUrl, request, String.class);
            log.info("Tool execution alert sent successfully");
        } catch (Exception e) {
            log.error("Failed to send tool execution alert: {}", e.getMessage());
            log.debug("Alert error details", e);
        }
    }

    private String formatActionDescription(String toolName) {
        return switch (toolName) {
            case "start_server" -> "Start Server";
            case "stop_server" -> "Stop Server";
            case "restart_server" -> "Restart Server";
            default -> toolName;
        };
    }
}
