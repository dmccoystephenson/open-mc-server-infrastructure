package com.openmc.minecraftwrapper.service;

import com.openmc.minecraftwrapper.model.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AlertService {

    @Value("${alert.manager.url:http://alert-manager:8090/api/alerts}")
    private String alertManagerUrl;

    @Value("${alerts.server.start:true}")
    private boolean alertsServerStart;

    @Value("${alerts.server.stop:true}")
    private boolean alertsServerStop;

    @Value("${alerts.server.crash:true}")
    private boolean alertsServerCrash;

    @Value("${alerts.plugin.deploy:true}")
    private boolean alertsPluginDeploy;

    private final RestTemplate restTemplate;

    public AlertService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void sendAlert(String title, String message, String level, String alertToggle) {
        sendAlert(title, message, level, alertToggle, Collections.singletonList("DISCORD"));
    }

    public void sendAlert(String title, String message, String level, String alertToggle, List<String> destinations) {
        // Check if this type of alert is enabled
        if (!isAlertEnabled(alertToggle)) {
            log.info("Alert skipped (disabled via {}): {}", alertToggle, title);
            return;
        }

        // Validate alert manager URL is configured
        if (alertManagerUrl == null || alertManagerUrl.trim().isEmpty()) {
            log.warn("Alert manager URL is not configured. Skipping alert: {}", title);
            return;
        }

        Alert alert = Alert.builder()
                .title(title)
                .message(message)
                .level(level)
                .source("minecraft-server")
                .destinations(destinations)
                .build();

        try {
            log.info("Sending alert to {}: {} ({})", alertManagerUrl, title, level);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Alert> request = new HttpEntity<>(alert, headers);
            
            restTemplate.postForEntity(alertManagerUrl, request, String.class);
            log.info("Alert sent successfully");
        } catch (Exception e) {
            log.error("Failed to send alert: {}", e.getMessage());
            log.debug("Alert error details", e);
        }
    }

    private boolean isAlertEnabled(String alertToggle) {
        if (alertToggle == null || alertToggle.isEmpty()) {
            return true;
        }

        return switch (alertToggle) {
            case "ALERTS_SERVER_START" -> alertsServerStart;
            case "ALERTS_SERVER_STOP" -> alertsServerStop;
            case "ALERTS_SERVER_CRASH" -> alertsServerCrash;
            case "ALERTS_PLUGIN_DEPLOY" -> alertsPluginDeploy;
            default -> true;
        };
    }

    public void sendServerStartAlert() {
        sendAlert("Minecraft Server Started", 
                 "The Minecraft server has started successfully.", 
                 "INFO", 
                 "ALERTS_SERVER_START");
    }

    public void sendServerStopAlert() {
        sendAlert("Minecraft Server Stopped", 
                 "The Minecraft server has been shut down gracefully.", 
                 "INFO", 
                 "ALERTS_SERVER_STOP");
    }

    public void sendServerCrashAlert(int exitCode) {
        sendAlert("Minecraft Server Crashed", 
                 String.format("The Minecraft server exited unexpectedly with code %d. Check logs for details.", exitCode), 
                 "ERROR", 
                 "ALERTS_SERVER_CRASH");
    }

    public void sendPluginDeploySuccessAlert(String pluginName) {
        sendAlert("Plugin Deployed Successfully",
                 String.format("Plugin '%s' was deployed successfully.", pluginName),
                 "INFO",
                 "ALERTS_PLUGIN_DEPLOY");
    }

    public void sendPluginDeployFailureAlert(String pluginName, String reason) {
        sendAlert("Plugin Deployment Failed",
                 String.format("Deployment of plugin '%s' failed: %s", pluginName, reason),
                 "ERROR",
                 "ALERTS_PLUGIN_DEPLOY");
    }
}
