package com.openmc.upgrademanager.service;

import com.openmc.upgrademanager.model.MinecraftVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Service for checking Minecraft version updates
 */
@Service
@Slf4j
public class MinecraftVersionService {

    @Value("${env.file.path:/.env}")
    private String envFilePath;

    @Value("${alert.manager.url:http://alert-manager:8090/api/alerts}")
    private String alertManagerUrl;

    @Value("${alerts.version.check:true}")
    private boolean alertsVersionCheck;

    @Value("${version.check.enabled:true}")
    private boolean versionCheckEnabled;

    private final RestTemplate restTemplate;

    public MinecraftVersionService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Check for new Minecraft versions daily at 3 AM
     */
    @Scheduled(cron = "${version.check.schedule:0 0 3 * * ?}")
    public void performScheduledVersionCheck() {
        if (!versionCheckEnabled) {
            log.debug("Version check is disabled");
            return;
        }
        
        log.info("Starting scheduled version check");
        try {
            String currentVersion = getCurrentVersion();
            String latestVersion = getLatestMinecraftVersion();
            
            if (currentVersion == null || currentVersion.equals("unknown")) {
                log.warn("Could not determine current Minecraft version");
                return;
            }
            
            if (latestVersion == null) {
                log.warn("Could not determine latest Minecraft version");
                return;
            }
            
            if (!currentVersion.equals(latestVersion)) {
                log.warn("Server is outdated! Current: {}, Latest: {}", currentVersion, latestVersion);
                sendVersionAlert(currentVersion, latestVersion);
            } else {
                log.info("Server is up to date: {}", currentVersion);
            }
        } catch (Exception e) {
            log.error("Error during scheduled version check", e);
        }
    }

    /**
     * Get the current Minecraft version from .env file
     */
    public String getCurrentVersion() {
        Path envFile = Paths.get(envFilePath);
        
        if (!Files.exists(envFile)) {
            log.error(".env file not found at: {}", envFilePath);
            return "unknown";
        }
        
        try (BufferedReader reader = Files.newBufferedReader(envFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("MINECRAFT_VERSION=")) {
                    return line.substring("MINECRAFT_VERSION=".length()).trim();
                }
            }
        } catch (IOException e) {
            log.error("Error reading .env file", e);
        }
        
        return "unknown";
    }

    /**
     * Get the latest stable Minecraft version
     * TODO: Implement full Minecraft version manifest API integration
     * This would call: https://launchermeta.mojang.com/mc/game/version_manifest.json
     * and parse the JSON to get the latest release version
     */
    public String getLatestMinecraftVersion() {
        try {
            log.info("Checking for latest Minecraft version...");
            
            // For now, we return null to indicate we can't determine the latest version
            // without implementing full JSON parsing from the Minecraft version manifest API
            // This is intentional to avoid false alerts and requires future implementation
            return null;
            
        } catch (Exception e) {
            log.error("Error fetching latest Minecraft version", e);
            return null;
        }
    }

    /**
     * Send an alert about version mismatch
     */
    private void sendVersionAlert(String currentVersion, String latestVersion) {
        if (!alertsVersionCheck) {
            log.debug("Version check alerts are disabled");
            return;
        }
        
        try {
            Map<String, String> alert = new HashMap<>();
            alert.put("title", "Minecraft Version Outdated");
            alert.put("message", String.format(
                "Your server is running version %s, but the latest version is %s. Consider upgrading.",
                currentVersion, latestVersion
            ));
            alert.put("level", "WARNING");
            alert.put("source", "upgrade-manager");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(alert, headers);
            
            restTemplate.postForEntity(alertManagerUrl, request, String.class);
            log.info("Version alert sent successfully");
        } catch (Exception e) {
            log.error("Failed to send version alert", e);
        }
    }

    /**
     * Update the Minecraft version in .env file
     */
    public void updateEnvVersion(String newVersion) throws IOException {
        Path envFile = Paths.get(envFilePath);
        
        if (!Files.exists(envFile)) {
            throw new IOException(".env file not found at: " + envFilePath);
        }
        
        StringBuilder content = new StringBuilder();
        boolean updated = false;
        
        try (BufferedReader reader = Files.newBufferedReader(envFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("MINECRAFT_VERSION=")) {
                    content.append("MINECRAFT_VERSION=").append(newVersion).append("\n");
                    updated = true;
                } else {
                    content.append(line).append("\n");
                }
            }
        }
        
        if (!updated) {
            content.append("MINECRAFT_VERSION=").append(newVersion).append("\n");
        }
        
        Files.writeString(envFile, content.toString());
        log.info("Updated MINECRAFT_VERSION to {} in .env", newVersion);
    }
}
