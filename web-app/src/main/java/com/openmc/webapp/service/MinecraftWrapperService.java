package com.openmc.webapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for interacting with the Minecraft Wrapper REST API
 */
@Service
@ConditionalOnProperty(name = "minecraft.wrapper.enabled", havingValue = "true", matchIfMissing = true)
public class MinecraftWrapperService {

    private static final Logger log = LoggerFactory.getLogger(MinecraftWrapperService.class);

    @Value("${minecraft.wrapper.url:http://minecraft-wrapper:8092}")
    private String wrapperUrl;

    private final RestTemplate restTemplate;
    private final RestTemplate uploadRestTemplate;

    public MinecraftWrapperService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.uploadRestTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofMinutes(10))
                .build();
    }

    /**
     * Get the current server status from the wrapper
     * @return Server status information, or null if not available
     */
    public ServerStatus getServerStatus() {
        try {
            String url = wrapperUrl + "/api/server/status";
            log.debug("Fetching server status from: {}", url);
            
            ResponseEntity<ServerStatus> response = restTemplate.getForEntity(url, ServerStatus.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Failed to get server status from wrapper: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Send a command to the Minecraft server via the wrapper
     * @param command Command to send
     * @return true if command was sent successfully
     */
    public boolean sendCommand(String command) {
        try {
            String url = wrapperUrl + "/api/server/command";
            log.info("Sending command to server via wrapper: {}", command);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            HttpEntity<String> request = new HttpEntity<>(command, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to send command to wrapper: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Send a message to players via the wrapper
     * @param text Message text
     * @param destination Destination (MINECRAFT or DISCORD)
     * @return true if message was sent successfully
     */
    public boolean sendMessage(String text, String destination) {
        try {
            String url = wrapperUrl + "/api/messages";
            log.info("Sending message via wrapper: {}", text);
            
            Map<String, String> messageRequest = new HashMap<>();
            messageRequest.put("text", text);
            messageRequest.put("destination", destination);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(messageRequest, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to send message via wrapper: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Start the Minecraft server via the wrapper.
     * The start endpoint returns immediately (202 Accepted) and performs start asynchronously.
     * @return true if start was initiated successfully
     */
    public boolean startServer() {
        try {
            String url = wrapperUrl + "/api/server/start";
            log.info("Starting server via wrapper");
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to start server via wrapper: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Stop the Minecraft server via the wrapper.
     * The stop endpoint returns immediately (202 Accepted) and performs stop asynchronously.
     * @return true if stop was initiated successfully
     */
    public boolean stopServer() {
        try {
            String url = wrapperUrl + "/api/server/stop";
            log.info("Stopping server via wrapper");
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to stop server via wrapper: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Restart the Minecraft server via the wrapper.
     * The restart endpoint returns immediately (202 Accepted) and performs restart asynchronously.
     * @return true if restart was initiated successfully
     */
    public boolean restartServer() {
        try {
            String url = wrapperUrl + "/api/server/restart";
            log.info("Restarting server via wrapper");
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to restart server via wrapper: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Initiate graceful server shutdown via the wrapper.
     * The shutdown endpoint returns immediately (202 Accepted) and performs shutdown asynchronously.
     * @return true if shutdown was initiated successfully
     */
    public boolean initiateShutdown() {
        try {
            String url = wrapperUrl + "/api/server/shutdown";
            log.info("Initiating server shutdown via wrapper");
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            // Accept both 200 OK and 202 Accepted as success
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to initiate shutdown via wrapper: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Upload a world ZIP archive to the wrapper, which stops the server, replaces the world,
     * and restarts the server.
     *
     * @param file            the ZIP archive containing the world
     * @param deployAuthToken the Bearer token for the wrapper's deploy endpoint
     * @return true if the upload was accepted successfully
     */
    public boolean uploadWorld(MultipartFile file, String deployAuthToken) {
        try {
            String url = wrapperUrl + "/api/world/upload";
            log.info("Uploading world to wrapper");

            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "world.zip";
            long size = file.getSize();
            Resource resource = new InputStreamResource(file.getInputStream()) {
                @Override
                public String getFilename() {
                    return filename;
                }

                @Override
                public long contentLength() {
                    return size;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + deployAuthToken);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = uploadRestTemplate.postForEntity(url, request, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to upload world to wrapper: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if the wrapper service is available
     * @return true if wrapper is reachable
     */
    public boolean isAvailable() {
        return getServerStatus() != null;
    }

    /**
     * Server status data class
     */
    public static class ServerStatus {
        private boolean running;
        private Long pid;
        private String serverJar;
        private String serverDirectory;

        public ServerStatus() {
        }

        public boolean isRunning() {
            return running;
        }

        public void setRunning(boolean running) {
            this.running = running;
        }

        public Long getPid() {
            return pid;
        }

        public void setPid(Long pid) {
            this.pid = pid;
        }

        public String getServerJar() {
            return serverJar;
        }

        public void setServerJar(String serverJar) {
            this.serverJar = serverJar;
        }

        public String getServerDirectory() {
            return serverDirectory;
        }

        public void setServerDirectory(String serverDirectory) {
            this.serverDirectory = serverDirectory;
        }
    }
}
