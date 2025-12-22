package com.openmc.upgrademanager.service;

import com.openmc.upgrademanager.exception.UpgradeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class UpgradeService {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^MINECRAFT_VERSION=(.*)$");

    @Value("${env.file.path:/.env}")
    private String envFilePath;

    @Value("${backup.manager.url:http://backup-manager:8091/api/backups/trigger}")
    private String backupManagerUrl;

    @Value("${alert.manager.url:http://alert-manager:8090/api/alerts}")
    private String alertManagerUrl;

    @Value("${container.name:open-mc-server}")
    private String containerName;

    @Value("${alerts.upgrade.start:true}")
    private boolean alertsUpgradeStart;

    @Value("${alerts.upgrade.complete:true}")
    private boolean alertsUpgradeComplete;

    @Value("${alerts.upgrade.failure:true}")
    private boolean alertsUpgradeFailure;

    @Value("${docker.compose.file:/docker-compose-path/compose.yml}")
    private String dockerComposeFile;

    private final RestTemplate restTemplate;

    public UpgradeService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Perform the upgrade operation
     */
    public Map<String, Object> performUpgrade(String newVersion) throws UpgradeException {
        log.info("Starting upgrade to version {}", newVersion);
        
        Map<String, Object> result = new HashMap<>();
        
        // Get current version
        String currentVersion = getCurrentVersion();
        log.info("Current version: {}", currentVersion);
        result.put("previousVersion", currentVersion);
        result.put("newVersion", newVersion);

        // Send upgrade start alert
        sendAlert("Server Upgrade Started", 
                 String.format("Starting upgrade from %s to %s", currentVersion, newVersion),
                 "INFO", alertsUpgradeStart);

        // Step 1: Check for backup or create one
        log.info("Step 1/6: Ensuring backup exists...");
        String backupPath = ensureBackupExists();
        result.put("backupPath", backupPath);
        log.info("Backup verified: {}", backupPath);

        // Step 2: Stop the server
        log.info("Step 2/6: Stopping the server...");
        if (isServerRunning()) {
            stopServer();
            log.info("Server stopped successfully");
        } else {
            log.info("Server is not running, skipping stop step");
        }

        // Step 3: Update version in .env
        log.info("Step 3/6: Updating MINECRAFT_VERSION in .env...");
        updateEnvVersion(newVersion);
        log.info("Version updated in .env");

        // Step 4: Rebuild Docker image
        log.info("Step 4/6: Rebuilding Docker image with new version...");
        log.info("This may take 10-15 minutes as it compiles Spigot from source...");
        try {
            rebuildDockerImage();
            log.info("Docker image rebuilt successfully");
        } catch (UpgradeException e) {
            log.error("Docker build failed", e);
            sendAlert("Server Upgrade Failed",
                     String.format("Upgrade from %s to %s failed during Docker build. Backup: %s", 
                                   currentVersion, newVersion, backupPath),
                     "ERROR", alertsUpgradeFailure);
            throw e;
        }

        // Step 5: Start the server
        log.info("Step 5/6: Starting the server...");
        startServer();
        log.info("Server started");

        // Step 6: Monitor startup
        log.info("Step 6/6: Monitoring server startup...");
        monitorStartup();

        // Send completion alert
        sendAlert("Server Upgrade Complete",
                 String.format("Successfully upgraded from %s to %s. Backup: %s", 
                               currentVersion, newVersion, backupPath),
                 "INFO", alertsUpgradeComplete);

        log.info("Upgrade completed successfully");
        result.put("success", true);
        result.put("message", String.format("Successfully upgraded from %s to %s", currentVersion, newVersion));
        
        return result;
    }

    /**
     * Get the current Minecraft version from .env file
     */
    String getCurrentVersion() throws UpgradeException {
        Path envFile = Paths.get(envFilePath);
        
        if (!Files.exists(envFile)) {
            log.warn(".env file not found at {}, returning 'unknown'", envFilePath);
            return "unknown";
        }

        try {
            return Files.lines(envFile)
                    .map(VERSION_PATTERN::matcher)
                    .filter(Matcher::matches)
                    .map(m -> m.group(1))
                    .findFirst()
                    .orElse("unknown");
        } catch (IOException e) {
            throw new UpgradeException("Failed to read .env file", e);
        }
    }

    /**
     * Update the Minecraft version in .env file
     */
    void updateEnvVersion(String newVersion) throws UpgradeException {
        Path envFile = Paths.get(envFilePath);
        
        if (!Files.exists(envFile)) {
            throw new UpgradeException(".env file not found at " + envFilePath);
        }

        try {
            String content = Files.readString(envFile);
            
            // Update or add MINECRAFT_VERSION
            if (content.contains("MINECRAFT_VERSION=")) {
                content = content.replaceFirst("MINECRAFT_VERSION=.*", "MINECRAFT_VERSION=" + newVersion);
            } else {
                content = content + "\nMINECRAFT_VERSION=" + newVersion + "\n";
            }
            
            Files.writeString(envFile, content);
            log.info("Updated MINECRAFT_VERSION to {} in .env", newVersion);
        } catch (IOException e) {
            throw new UpgradeException("Failed to update .env file", e);
        }
    }

    /**
     * Ensure a backup exists, create one if needed
     */
    String ensureBackupExists() throws UpgradeException {
        log.info("Checking for recent backup...");
        
        // Try to find recent backup
        Path backupsDir = Paths.get("/backups");
        if (Files.exists(backupsDir)) {
            try {
                var recentBackup = Files.list(backupsDir)
                        .filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().startsWith("backup-"))
                        .filter(p -> Files.exists(p.resolve("mcserver-backup.tar.gz")))
                        .max((p1, p2) -> {
                            try {
                                return Files.getLastModifiedTime(p1).compareTo(Files.getLastModifiedTime(p2));
                            } catch (IOException e) {
                                return 0;
                            }
                        });
                
                if (recentBackup.isPresent()) {
                    String backupPath = recentBackup.get().toString();
                    log.info("Using existing backup: {}", backupPath);
                    return backupPath;
                }
            } catch (IOException e) {
                log.warn("Error checking for existing backups", e);
            }
        }

        // No recent backup found, create one
        log.info("No valid backup found. Creating a new backup...");
        return triggerBackup();
    }

    /**
     * Trigger a backup via the backup-manager API
     */
    String triggerBackup() throws UpgradeException {
        try {
            log.info("Triggering backup via {}", backupManagerUrl);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>("{}", headers);
            
            var response = restTemplate.postForEntity(backupManagerUrl, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (Boolean.TRUE.equals(body.get("success"))) {
                    String backupPath = (String) body.get("backupPath");
                    log.info("Backup created successfully: {}", backupPath);
                    return backupPath;
                } else {
                    throw new UpgradeException("Backup failed: " + body.get("message"));
                }
            } else {
                throw new UpgradeException("Backup request failed with status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new UpgradeException("Failed to trigger backup", e);
        }
    }

    /**
     * Check if the server container is running
     */
    boolean isServerRunning() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "ps", "--format", "{{.Names}}");
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.lines().anyMatch(line -> line.equals(containerName));
            }
        } catch (IOException e) {
            log.error("Failed to check if server is running", e);
            return false;
        }
    }

    /**
     * Stop the server using down.sh script
     */
    void stopServer() throws UpgradeException {
        try {
            ProcessBuilder pb = new ProcessBuilder("/scripts/down.sh");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new UpgradeException("Failed to stop server, exit code: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new UpgradeException("Failed to execute down.sh", e);
        }
    }

    /**
     * Start the server using up.sh script
     */
    void startServer() throws UpgradeException {
        try {
            ProcessBuilder pb = new ProcessBuilder("/scripts/up.sh");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new UpgradeException("Failed to start server, exit code: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new UpgradeException("Failed to execute up.sh", e);
        }
    }

    /**
     * Rebuild the Docker image with the new version
     */
    void rebuildDockerImage() throws UpgradeException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "compose", "-f", dockerComposeFile, "build", "--no-cache"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Log output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("docker build: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new UpgradeException("Docker build failed with exit code: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new UpgradeException("Failed to rebuild Docker image", e);
        }
    }

    /**
     * Monitor server startup by checking logs
     */
    void monitorStartup() {
        log.info("Waiting for server to initialize...");
        try {
            Thread.sleep(5000);
            
            // Show recent logs
            ProcessBuilder pb = new ProcessBuilder("docker", "logs", containerName, "--tail", "20");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            log.info("Recent server logs:");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("  {}", line);
                }
            }
        } catch (InterruptedException | IOException e) {
            log.warn("Could not retrieve startup logs", e);
        }
    }

    /**
     * Send an alert to the alert manager
     */
    void sendAlert(String title, String message, String level, boolean enabled) {
        if (!enabled) {
            log.debug("Alert skipped (disabled): {}", title);
            return;
        }

        try {
            Map<String, String> alertData = new HashMap<>();
            alertData.put("title", title);
            alertData.put("message", message);
            alertData.put("level", level);
            alertData.put("source", "upgrade-manager");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(alertData, headers);

            log.info("Sending alert to {}: {} ({})", alertManagerUrl, title, level);
            restTemplate.postForEntity(alertManagerUrl, request, String.class);
            log.info("Alert sent successfully");
        } catch (Exception e) {
            log.warn("Failed to send alert: {}", e.getMessage());
        }
    }
}
