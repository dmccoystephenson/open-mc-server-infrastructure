package com.openmc.agentmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service for calling the minecraft-wrapper REST API.
 */
@Slf4j
@Service
public class MinecraftWrapperService {

    private final RestTemplate restTemplate;

    @Value("${minecraft.wrapper.url:http://minecraft-wrapper:8092}")
    private String wrapperUrl;

    public MinecraftWrapperService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Start the Minecraft server.
     * @return response message from the wrapper
     */
    public String startServer() {
        log.info("Calling minecraft-wrapper to start server");
        return callWrapper("/api/server/start", "start");
    }

    /**
     * Stop the Minecraft server (graceful shutdown with player warnings).
     * @return response message from the wrapper
     */
    public String stopServer() {
        log.info("Calling minecraft-wrapper to stop server");
        return callWrapper("/api/server/stop", "stop");
    }

    /**
     * Restart the Minecraft server (graceful stop then start).
     * @return response message from the wrapper
     */
    public String restartServer() {
        log.info("Calling minecraft-wrapper to restart server");
        return callWrapper("/api/server/restart", "restart");
    }

    /**
     * Get the current server status.
     * @return response message from the wrapper
     */
    public String getServerStatus() {
        log.info("Calling minecraft-wrapper to get server status");
        String url = wrapperUrl + "/api/server/status";
        try {
            log.debug("Sending GET request to minecraft-wrapper: {}", url);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            String body = response.getBody();
            log.info("Minecraft wrapper status response: {} - {}", response.getStatusCode(), body);
            return body != null ? body : "No status information available";
        } catch (Exception e) {
            log.error("Failed to get server status via minecraft-wrapper at {}: {}", url, e.getMessage(), e);
            throw new RuntimeException("Failed to get server status: " + e.getMessage(), e);
        }
    }

    /**
     * Get the last {@code lines} lines of the Minecraft server log.
     * Requires {@code logs.diagnostic.enabled=true} on the wrapper side.
     *
     * @param lines number of log lines to retrieve
     * @return JSON string from the wrapper ({@code {"lines":[...],"count":N}})
     */
    public String getServerLogs(int lines) {
        log.info("Calling minecraft-wrapper for server logs ({} lines)", lines);
        String url = wrapperUrl + "/api/server/logs?lines=" + lines;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            String body = response.getBody();
            return body != null ? body : "{\"lines\":[],\"count\":0}";
        } catch (Exception e) {
            log.error("Failed to get server logs via minecraft-wrapper at {}: {}", url, e.getMessage(), e);
            throw new RuntimeException("Failed to get server logs: " + e.getMessage(), e);
        }
    }

    /**
     * Get server performance metrics (heap, process memory, uptime, TPS).
     *
     * @return JSON string from the wrapper containing {@link com.openmc.minecraftwrapper.model.ServerMetrics} fields
     */
    public String getServerMetrics() {
        log.info("Calling minecraft-wrapper for server metrics");
        String url = wrapperUrl + "/api/server/metrics";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            String body = response.getBody();
            return body != null ? body : "{}";
        } catch (Exception e) {
            log.error("Failed to get server metrics via minecraft-wrapper at {}: {}", url, e.getMessage(), e);
            throw new RuntimeException("Failed to get server metrics: " + e.getMessage(), e);
        }
    }

    private String callWrapper(String path, String action) {
        String url = wrapperUrl + path;
        try {
            log.debug("Sending POST request to minecraft-wrapper: {}", url);
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            String body = response.getBody();
            log.info("Minecraft wrapper {} response: {} - {}", action, response.getStatusCode(), body);
            return body != null ? body : "Server " + action + " initiated";
        } catch (Exception e) {
            log.error("Failed to {} server via minecraft-wrapper at {}: {}", action, url, e.getMessage(), e);
            throw new RuntimeException("Failed to " + action + " server: " + e.getMessage(), e);
        }
    }
}
