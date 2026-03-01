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
