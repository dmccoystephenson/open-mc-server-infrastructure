package com.openmc.minecraftwrapper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class WebAppNotificationService {

    @Value("${webapp.url:}")
    private String webappUrl;

    @Value("${deployment.auth.token:}")
    private String deploymentAuthToken;

    private final RestTemplate restTemplate;

    public WebAppNotificationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Notify the web app about a successful deployment.
     */
    public void notifyDeploymentSuccess(String pluginName, String branch, String repoUrl) {
        sendDeploymentNotification(pluginName, "SUCCESS", "automated", branch, repoUrl,
                "Plugin deployed successfully");
    }

    /**
     * Notify the web app about a failed deployment.
     */
    public void notifyDeploymentFailure(String pluginName, String reason, String branch, String repoUrl) {
        sendDeploymentNotification(pluginName, "FAILURE", "automated", branch, repoUrl, reason);
    }

    private void sendDeploymentNotification(String pluginName, String status, String source,
                                             String branch, String repoUrl, String message) {
        if (webappUrl == null || webappUrl.trim().isEmpty()) {
            log.debug("Web app URL is not configured. Skipping deployment notification.");
            return;
        }

        String url = webappUrl.replaceAll("/+$", "") + "/api/deployment-history";

        Map<String, String> payload = new HashMap<>();
        payload.put("pluginName", pluginName);
        payload.put("status", status);
        payload.put("source", source);
        if (branch != null) {
            payload.put("branch", branch);
        }
        if (repoUrl != null) {
            payload.put("repoUrl", repoUrl);
        }
        if (message != null) {
            payload.put("message", message);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (deploymentAuthToken != null && !deploymentAuthToken.trim().isEmpty()) {
                headers.set("Authorization", "Bearer " + deploymentAuthToken);
            }
            HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(url, request, String.class);
            log.info("Deployment notification sent to web app: plugin={}, status={}", pluginName, status);
        } catch (Exception e) {
            log.error("Failed to send deployment notification to web app: {}", e.getMessage());
            log.debug("Deployment notification error details", e);
        }
    }
}
